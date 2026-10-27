import test from 'node:test';
import assert from 'node:assert/strict';
import { createReCatch, monthlyStatement, feeFor } from '../core/index.js';
import { merchants, products, wallet, at } from './helpers.js';

const app = () => createReCatch({ merchants: merchants(), products: products(), wallet: wallet() });

test('수수료는 웹 시연과 같은 반올림 (26,000원 → 5,200원)', () => {
  assert.equal(feeFor(26000), 5200);
  assert.equal(feeFor(2509), 502);
  assert.equal(feeFor(-100), 0);
});

test('월 정산: 완료 건만 과금, 첫 달 쿠폰은 수수료를 넘지 않음', () => {
  const s = monthlyStatement([
    { id: 1, status: 'done', delta: 2500 },
    { id: 2, status: 'pending', delta: 1300 },
    { id: 3, status: 'missed', delta: 1960 }
  ], { firstMonth: true });
  assert.equal(s.fee, 500);
  assert.equal(s.coupon, 500);
  assert.equal(s.charge, 0);
  assert.equal(s.net, 2500);
  assert.equal(s.pending, 1300);
  assert.equal(s.missed, 1960);
});

test('프리미엄은 수수료 면제, 구독료만', () => {
  const s = monthlyStatement([{ id: 1, status: 'done', delta: 26000 }], { premium: true });
  assert.equal(s.fee, 0);
  assert.equal(s.charge, 2900);
  assert.equal(s.net, 23100);
});

test('흐름: 현장할인 재결제 → 취소·재승인 알림으로 자동 완료', () => {
  const rc = app();
  const r1 = rc.onNotification('현대카드 승인 18,000원 일시불 10/16 08:10 파리바게뜨경산영대', { now: at('08:10') });
  // 현대 0.7%(126) vs T멤버십 10%(1,800) + 남은 16,200원에 현대 0.7%(113): 차액 1,787
  assert.equal(r1.recommendation.delta, 1787);
  assert.equal(r1.recommendation.best.cardLast4, '5530');

  rc.onNotification('현대카드 취소 18,000원 10/16 08:11 파리바게뜨경산영대', { now: at('08:11') });
  const r3 = rc.onNotification('현대카드 승인 16,200원 일시불 10/16 08:11 파리바게뜨경산영대', { now: at('08:11') });
  assert.equal(r3.recommendation, null, '재결제 승인으로 새 추천을 만들지 않음');
  assert.equal(r3.updates.length, 1);
  assert.equal(r3.updates[0].observedPOS, 1800);
  // 카드 0.7%는 청구할인이라 확정 대기
  assert.equal(r3.updates[0].status, 'pending');
  const done = rc.confirm(r1.recommendation.id, 113);
  assert.equal(done.status, 'done');
  assert.equal(done.delta, 1787);
  assert.deepEqual(rc.toServerRecord(r1.recommendation.id), { id: 1, date: '2026-10-16', brand: '파리바게뜨', category: 'bake', status: 'done', delta: 1787 });
});

test('흐름: 현장에서 기대만큼 안 깎이면 덜 깎인 만큼만 인정', () => {
  const rc = app();
  const { recommendation: rec } = rc.onNotification('현대카드 승인 18,000원 일시불 10/16 08:10 파리바게뜨경산영대', { now: at('08:10') });
  rc.onNotification('현대카드 취소 18,000원 10/16 08:11 파리바게뜨경산영대', { now: at('08:11') });
  const { updates } = rc.onNotification('현대카드 승인 17,100원 일시불 10/16 08:11 파리바게뜨경산영대', { now: at('08:11') });
  assert.equal(updates[0].observedPOS, 900);
  assert.equal(updates[0].delta, rec.delta - 900);
});

test('흐름: 아무것도 안 하면 놓친 혜택, 취소만 하면 abandoned, 재승인만 오면 이중결제 경고', () => {
  let rc = app();
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:44\n74,000원\n빕스동대구역점 사용', { now: at('19:44') });
  assert.equal(rc.tick({ now: at('19:50') }).length, 0);
  assert.equal(rc.tick({ now: at('19:55') })[0].status, 'missed');

  rc = app();
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:44\n74,000원\n빕스동대구역점 사용', { now: at('19:44') });
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:45\n74,000원\n빕스동대구역점 취소', { now: at('19:45') });
  assert.equal(rc.tick({ now: at('19:55') })[0].status, 'abandoned');

  rc = app();
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:44\n74,000원\n빕스동대구역점 사용', { now: at('19:44') });
  const { updates } = rc.onNotification('현대카드 승인 62,900원 일시불 10/16 19:46 빕스동대구역점', { now: at('19:46') });
  assert.equal(updates[0].status, 'double_paid');
});

test('누적 금액이 있는 문자는 이번 달 실적을 갱신한다', () => {
  const w = wallet();
  const rc = createReCatch({ merchants: merchants(), products: products(), wallet: w });
  rc.onNotification('[Web발신]\n신한카드(1083)승인 홍*동 6,500원(일시불)10/16 22:41 GS25영남대정문 누적268,000원', { now: at('22:41') });
  assert.equal(w.cards.find(c => c.last4 === '1083').monthSpend, 268000);
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 12:30\n13,000원\n스타벅스영남대정 사용', { now: at('12:30') });
  assert.equal(w.cards.find(c => c.last4 === '9421').monthSpend, 293000);
});

test('혜택 한도 사용액: 승인하면 쌓이고 같은 결제를 취소하면 빠진다', () => {
  const w = wallet();
  const rc = createReCatch({ merchants: merchants(), products: products(), wallet: w });
  const kb = () => w.cards.find(c => c.last4 === '9421');
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 12:30\n13,000원\n스타벅스영남대정 사용', { now: at('12:30') });
  assert.equal(kb().usage['kb-tt-starbucks'], 2600);
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 12:40\n40,000원\n스타벅스영남대정 사용', { now: at('12:40') });
  assert.equal(kb().usage['kb-tt-starbucks'], 10000, '월 한도 10,000원에서 멈춤');
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 12:41\n40,000원\n스타벅스영남대정 취소', { now: at('12:41') });
  assert.equal(kb().usage['kb-tt-starbucks'], 2600);
});

test('한도를 다 쓰면 그 카드로는 더 추천하지 않는다', () => {
  const w = wallet();
  w.cards.find(c => c.last4 === '9421').usage = { 'kb-tt-starbucks': 10000 };
  const rc = createReCatch({ merchants: merchants(), products: products(), wallet: w });
  const r = rc.onNotification('[현대카드]승인\n홍*동\n13,000원 일시불\n10/16 12:30\n스타벅스영남대정', { now: at('12:30', 16) });
  assert.equal(r.recommendation, null);
  assert.equal(r.reason, 'below_threshold');
});

test('달이 바뀌면 이번 달 사용액이 전월 실적으로 넘어가고 한도가 초기화된다', () => {
  const w = wallet();
  const rc = createReCatch({ merchants: merchants(), products: products(), wallet: w });
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 12:30\n13,000원\n스타벅스영남대정 사용', { now: at('12:30') });
  const kb = w.cards.find(c => c.last4 === '9421');
  assert.equal(kb.monthSpend, 293000);
  rc.onNotification('[Web발신]\nKB국민체크(9421)\n홍*동님\n11/01 09:00\n5,000원\n스타벅스영남대정 사용', { now: new Date(2026, 10, 1, 9, 0) });
  assert.equal(kb.prevMonthSpend, 293000);
  assert.equal(kb.monthSpend, 5000);
  // 10월 사용액 293,000원 < 전월 실적 기준 300,000원 → 11월엔 혜택이 없어 사용액도 0
  assert.deepEqual(kb.usage, {});
  assert.equal(w.month, '2026-11');
});
