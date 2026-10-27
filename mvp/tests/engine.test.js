import test from 'node:test';
import assert from 'node:assert/strict';
import { recommend, enumerateOptions } from '../core/index.js';
import { products, wallet, at } from './helpers.js';

const ctx = () => ({ products: products(), wallet: wallet() });
const pay = (brand, category, amount, cardLast4, hm = '12:30') => ({ amount, merchant: { brand, category }, at: at(hm).toISOString(), cardLast4 });
const now = at('12:31');

test('현대 0.7%로 스타벅스 결제 → KB 톡톡D 20% 재결제 추천, 기준선 대비 차액에만 수수료', () => {
  const r = recommend(pay('스타벅스', 'cafe', 13000, '5530'), ctx(), { now });
  assert.equal(r.recommend, true);
  assert.equal(r.best.cardLast4, '9421');
  assert.equal(r.best.total, 2600);
  assert.equal(r.baseline.total, 91);
  assert.equal(r.delta, 2509);
  assert.equal(r.fee, 502);
  assert.equal(r.net, 2007);
  assert.ok(r.warnings.some(w => w.code === 'confirmed_later'), '캐시백은 나중에 확정');
});

test('빕스: 멤버십 현장할인 후 남은 금액에 카드 혜택을 더한다', () => {
  const r = recommend(pay('빕스', 'dine', 74000, '9421', '19:44'), ctx(), { now: at('19:45') });
  assert.equal(r.best.membership.productId, 'skt-tmembership');
  assert.equal(r.best.cardLast4, '5530');
  assert.equal(r.best.membership.value, 11100);
  assert.equal(r.best.card.value, Math.floor(62900 * 0.7 / 100));
  assert.equal(r.best.atPOS, 11100);
  assert.equal(r.delta, 11100 + 440);
  assert.ok(r.steps.some(s => s.includes('T멤버십')));
});

test('전월 실적 미달 카드는 혜택 0원 → 차액이 작으면 알리지 않는다', () => {
  const r = recommend(pay('GS25', 'cvs', 6500, '1083', '22:41'), ctx(), { now: at('22:42') });
  assert.equal(r.recommend, false);
  assert.equal(r.reason, 'below_threshold');
  const sh = enumerateOptions(pay('GS25', 'cvs', 6500, '1083', '22:41'), ctx()).find(o => o.cardLast4 === '1083' && !o.membership);
  assert.equal(sh.card.reason, 'prev_month_unmet');
});

test('실적을 채우면 심야 시간대 혜택이 더 큰 쪽을 고른다', () => {
  const c = ctx();
  c.wallet.cards.find(x => x.last4 === '1083').prevMonthSpend = 300000;
  const opts = enumerateOptions(pay('GS25', 'cvs', 20000, '5530', '23:10'), c);
  const sh = opts.find(o => o.cardLast4 === '1083' && !o.membership);
  assert.equal(sh.card.benefitId, 'sh-ml-night');
  assert.equal(sh.card.value, 3000);
  const day = enumerateOptions(pay('GS25', 'cvs', 20000, '5530', '15:00'), c).find(o => o.cardLast4 === '1083' && !o.membership);
  assert.equal(day.card.benefitId, 'sh-ml-cvs');
});

test('월 한도를 이미 쓴 만큼 혜택이 줄어든다', () => {
  const c = ctx();
  c.wallet.cards.find(x => x.last4 === '9421').usage['kb-tt-starbucks'] = 9000;
  const r = recommend(pay('스타벅스', 'cafe', 13000, '5530'), c, { now });
  assert.equal(r.best.total, 1000);
  assert.equal(r.recommend, false, '차액 909원 < 최소 1,000원');
});

test('2분이 지나면 추천하지 않는다', () => {
  const r = recommend(pay('스타벅스', 'cafe', 13000, '5530'), ctx(), { now: at('12:33') });
  assert.deepEqual(r, { recommend: false, reason: 'window_passed' });
});

test('사용자가 이미 멤버십을 썼다고 하면 기준선에 포함한다', () => {
  const r = recommend(pay('빕스', 'dine', 74000, '9421', '19:44'), ctx(), { now: at('19:45'), baselineMembershipId: 'skt-tmembership' });
  assert.equal(r.recommend, false);
  assert.equal(r.delta, 440);
});

test('원래 카드 취소로 실적이 기준 아래로 떨어지면 경고', () => {
  const c = ctx();
  c.wallet.cards.find(x => x.last4 === '9421').monthSpend = 305000; // 이 결제 포함
  const r = recommend(pay('빕스', 'dine', 74000, '9421', '19:44'), c, { now: at('19:45') });
  const w = r.warnings.find(x => x.code === 'original_card_performance');
  assert.ok(w);
  assert.match(w.message, /69,000원/);
  assert.ok(r.warnings.some(x => x.code === 'check_refund_delay'), '원래 카드가 체크카드');
});

test('모르는 가맹점·모르는 카드', () => {
  assert.equal(recommend(pay(null, null, 5000, '5530'), ctx(), { now }).reason, 'unknown_merchant');
  assert.equal(recommend(pay('스타벅스', 'cafe', 5000, '0000'), ctx(), { now }).reason, 'unknown_card');
});
