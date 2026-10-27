import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createApp } from '../server/app.js';
import { memoryStore } from '../server/store.js';
import { loadCatalog } from '../server/catalog.js';
import { parseNotification } from '../core/index.js';

// 시각을 움직일 수 있는 서버를 띄운다.
async function boot(opts = {}) {
  let now = new Date(2026, 9, 16, 12, 30, 20);
  const handle = createApp({ store: memoryStore(), catalog: loadCatalog(), clock: () => new Date(now), ...opts });
  const server = createServer(handle);
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  let token = null;
  const call = async (method, path, body) => {
    const res = await fetch(base + path, {
      method,
      headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    return { status: res.status, body: await res.json() };
  };
  return {
    call, handle,
    async register(extra = {}) { const r = await call('POST', '/api/register', extra); token = r.body.token; return r; },
    setToken(t) { token = t; },
    at(h, m, s = 0) { now = new Date(2026, 9, 16, h, m, s); return now; },
    close: () => new Promise(r => server.close(r))
  };
}

// 앱이 하는 일과 같다: 단말에서 파싱한 필드만 보낸다.
const event = (text, receivedAt) => ({ ...parseNotification(text, { now: receivedAt }), receivedAt: receivedAt.toISOString() });

test('서버: 등록 없이 호출하면 401, 헬스체크는 공개', async t => {
  const s = await boot(); t.after(s.close);
  assert.equal((await s.call('GET', '/api/health')).status, 200);
  assert.equal((await s.call('GET', '/api/wallet')).status, 401);
  s.setToken('wrong');
  assert.equal((await s.call('GET', '/api/recommendations')).status, 401);
});

test('서버: 초대 코드가 설정되면 맞아야 등록된다', async t => {
  const s = await boot({ invite: 'yu2026' }); t.after(s.close);
  assert.equal((await s.call('POST', '/api/register', {})).status, 403);
  assert.equal((await s.register({ invite: 'yu2026' })).status, 200);
});

test('서버: 승인 → 추천 → 취소·재승인 → 판정 → 확정 → 월 정산', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();

  const r1 = await s.call('POST', '/api/events', event('[현대카드]승인\n홍*동\n13,000원 일시불\n10/16 12:30\n스타벅스영남대정\n누적350,000원', s.at(12, 30, 15)));
  assert.equal(r1.status, 200);
  const rec = r1.body.recommendation;
  assert.equal(rec.store, '스타벅스');
  assert.equal(rec.best.cardLast4, '9421');
  assert.equal(rec.delta, 2509);
  assert.equal(rec.fee, 502);
  assert.equal(rec.status, 'waiting');
  assert.ok(Date.parse(rec.expiresAt) > Date.parse('2026-10-16T03:31:00Z'), '단말 수신 시각 기준 2분');

  await s.call('POST', '/api/events', event('[현대카드]취소\n홍*동\n13,000원\n10/16 12:31\n스타벅스영남대정', s.at(12, 31, 0)));
  const r3 = await s.call('POST', '/api/events', event('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 12:31\n13,000원\n스타벅스영남대정 사용', s.at(12, 31, 30)));
  assert.equal(r3.body.recommendation, null);
  assert.equal(r3.body.updates[0].status, 'pending');

  const c = await s.call('POST', `/api/recommendations/${rec.id}/confirm`, { confirmedLater: 2600 });
  assert.equal(c.body.status, 'done');

  const st = await s.call('GET', '/api/statement?month=2026-10');
  assert.equal(st.body.recovered, 2509);
  assert.equal(st.body.fee, 502);
  assert.equal(st.body.coupon, 502, '첫 달 쿠폰은 수수료 이하');
  assert.equal(st.body.charge, 0);
});

test('서버: 시간이 지나면 주기 판정으로 missed', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const r = await s.call('POST', '/api/events', event('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:44\n74,000원\n빕스동대구역점 사용', s.at(19, 44, 5)));
  assert.equal(r.body.recommendation.delta, 11540);
  s.at(19, 50);
  assert.equal(s.handle.tickAll(), 0);
  s.at(19, 55);
  assert.equal(s.handle.tickAll(), 1);
  const list = await s.call('GET', '/api/recommendations');
  assert.equal(list.body[0].status, 'missed');
  assert.equal(list.body[0].fee, 0, '놓친 혜택에는 수수료 없음');
});

test('서버: "이미 멤버십 썼어요" → 기준선 재계산, 더 아낄 게 없으면 no_gain', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const r = await s.call('POST', '/api/events', event('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:44\n74,000원\n빕스동대구역점 사용', s.at(19, 44, 5)));
  const b = await s.call('POST', `/api/recommendations/${r.body.recommendation.id}/baseline`, { membershipId: 'skt-tmembership' });
  assert.equal(b.body.status, 'no_gain');
  assert.equal(b.body.delta, 0);
});

test('서버: 오래된 알림(늦게 전송)은 추천하지 않는다', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const received = new Date(2026, 9, 16, 12, 30, 0);
  s.at(12, 40);
  const r = await s.call('POST', '/api/events', event('[현대카드]승인\n홍*동\n13,000원 일시불\n10/16 12:30\n스타벅스영남대정', received));
  assert.equal(r.body.recommendation, null);
  assert.equal(r.body.reason, 'window_passed');
});

test('서버: 입력 검사', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const bads = [
    { type: 'refund', amount: 1000 },
    { type: 'approve', amount: -5 },
    { type: 'approve', amount: 1.5 },
    { type: 'approve', amount: 1000, last4: '12a4' },
    { type: 'approve', amount: 1000, merchantRaw: 'x'.repeat(61) },
    { type: 'approve', amount: 1000, receivedAt: 'yesterday' }
  ];
  for (const b of bads) assert.equal((await s.call('POST', '/api/events', b)).status, 400, JSON.stringify(b));
  assert.equal((await s.call('PUT', '/api/wallet', { cards: [{ productId: 'nope', last4: '1111' }], memberships: [] })).status, 400);
  assert.equal((await s.call('PUT', '/api/wallet', { cards: [{ productId: 'hd-zero2', last4: '1111' }, { productId: 'kb-toktokd-check', last4: '1111' }], memberships: [] })).status, 400);
  assert.equal((await s.call('PUT', '/api/wallet', { cards: [], memberships: [{ productId: 'skt-tmembership', tier: 'DIAMOND' }] })).status, 400);
  assert.equal((await s.call('POST', '/api/events', { type: 'approve', amount: 1000, merchantRaw: 'a'.repeat(40000) })).status, 413);
});

test('서버: 지갑을 바꾸면 다음 계산부터 반영', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const w = await s.call('PUT', '/api/wallet', { cards: [{ productId: 'hd-zero2', last4: '5530', prevMonthSpend: 0 }], memberships: [] });
  assert.equal(w.status, 200);
  const r = await s.call('POST', '/api/events', event('[현대카드]승인\n홍*동\n13,000원 일시불\n10/16 12:30\n스타벅스영남대정', s.at(12, 30, 15)));
  assert.equal(r.body.recommendation, null, 'KB 카드가 없으니 추천할 것이 없음');
});

test('서버: 앱 지갑 화면처럼 GET 결과를 고쳐 그대로 PUT 할 수 있다', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const w = (await s.call('GET', '/api/wallet')).body;
  w.cards[0].usage = { 'kb-tt-starbucks': 9000 };
  w.cards.push({ productId: 'sh-mrlife', last4: '2222', prevMonthSpend: 350000, monthSpend: null });
  w.memberships = w.memberships.filter(m => m.productId !== 'cj-one');
  w.memberships.find(m => m.productId === 'skt-tmembership').tier = 'GOLD';
  const r = await s.call('PUT', '/api/wallet', w);
  assert.equal(r.status, 200);
  assert.equal(r.body.cards.length, 4);
  assert.equal(r.body.cards[3].issuer, 'sh', '카드사는 상품에서 채운다');
  assert.equal(r.body.memberships.length, 2);
  // 스타벅스 월 한도가 거의 찼으니 KB 추천이 1,000원 → 기준 미달
  const e = await s.call('POST', '/api/events', event('[현대카드]승인\n홍*동\n13,000원 일시불\n10/16 12:30\n스타벅스영남대정', s.at(12, 30, 15)));
  assert.equal(e.body.reason, 'below_threshold');
});

test('서버: 응답·저장소에 원문·이름이 없다', async t => {
  const store = memoryStore();
  const s = await boot({ store }); t.after(s.close);
  await s.register();
  const r = await s.call('POST', '/api/events', event('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:44\n74,000원\n빕스동대구역점 사용', s.at(19, 44, 5)));
  const dump = JSON.stringify(store.users) + JSON.stringify(r.body);
  assert.ok(!dump.includes('홍'));
  assert.ok(!dump.includes('Web발신'));
});

test('서버: 멤버십 바코드는 웹 시연과 같은 규칙으로 검사·정리, 직접 추가 멤버십 허용', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const w = (await s.call('GET', '/api/wallet')).body;
  const put = ms => s.call('PUT', '/api/wallet', { ...w, memberships: ms });
  const ok = await put([...w.memberships, { productId: null, name: '해피포인트', code: '1234-5678 9012', format: 'numeric' }]);
  assert.equal(ok.status, 200);
  assert.equal(ok.body.memberships.at(-1).code, '123456789012', '공백·하이픈 정리');
  assert.equal(ok.body.memberships.at(-1).productId, null);

  const bads = [
    [{ productId: null, name: '해피포인트', code: '1111 1111', format: 'numeric' }, 'invalid_barcode'],     // 같은 숫자 반복
    [{ productId: null, name: '상품', code: '8801234567891', format: 'ean13' }, 'invalid_barcode'],         // 검증 숫자 틀림
    [{ productId: null, name: '', code: '12345678' }, 'invalid_name'],
    [{ productId: null, name: '번호없음' }, 'code_required'],
    [{ productId: null, name: '중복', code: w.memberships[0].code, format: 'member16' }, 'duplicate_code']
  ];
  for (const [m, code] of bads) {
    const r = await put([...w.memberships, m]);
    assert.equal(r.status, 400, JSON.stringify(m));
    assert.equal(r.body.error, code);
  }
  const msg = await put([...w.memberships, { productId: null, name: 'x', code: '12', format: 'numeric' }]);
  assert.match(msg.body.message, /8~24자리/, '사용자에게 보여 줄 문구도 함께');
  assert.equal((await put([...w.memberships, { productId: null, name: 'EAN', code: '8801234567893', format: 'ean13' }])).status, 200);
});

test('서버: 조건(학생·지역·연령) 저장', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const w = (await s.call('GET', '/api/wallet')).body;
  const r = await s.call('PUT', '/api/wallet', { ...w, profile: { student: '대학원생', region: '대구시', age: '20대' } });
  assert.deepEqual(r.body.profile, { student: '대학원생', region: '대구시', age: '20대' });
  assert.equal((await s.call('PUT', '/api/wallet', { ...w, profile: { region: 'x'.repeat(31) } })).status, 400);
});

test('서버: "이번엔 넘어갈게요" → 놓친 혜택, 수수료 없음', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const r = await s.call('POST', '/api/events', event('[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:44\n74,000원\n빕스동대구역점 사용', s.at(19, 44, 5)));
  const k = await s.call('POST', `/api/recommendations/${r.body.recommendation.id}/skip`);
  assert.equal(k.body.status, 'missed');
  assert.equal(k.body.fee, 0);
  assert.equal((await s.call('POST', `/api/recommendations/${r.body.recommendation.id}/skip`)).status, 409);
  const st = await s.call('GET', '/api/statement');
  assert.equal(st.body.missed, 11540);
});

test('정산: 누적 청구액이 1만 원 미만이면 다음 달로 넘긴다', async () => {
  const { billingCycle } = await import('../core/index.js');
  const c = billingCycle([{ month: '2026-10', charge: 3000 }, { month: '2026-11', charge: 6000 }, { month: '2026-12', charge: 2000 }, { month: '2027-01', charge: 500 }]);
  assert.deepEqual(c.map(x => [x.carriedIn, x.totalDue, x.billed]), [[0, 3000, false], [3000, 9000, false], [9000, 11000, true], [0, 500, false]]);
});

test('서버: 명세서에 청구 예정일과 이월 정보', async t => {
  const s = await boot(); t.after(s.close);
  await s.register();
  const st = await s.call('GET', '/api/statement?month=2026-10');
  assert.equal(st.body.dueDate, '2026-10-31');
  assert.equal(st.body.threshold, 10000);
  assert.equal(st.body.billed, false);
});
