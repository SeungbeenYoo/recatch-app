// 폰 앱 흉내: 지금 시각으로 승인 문자를 만들어 "단말에서 파싱"한 뒤 파싱된 필드만 서버로 보낸다.
// 앱(Java)과 같은 파서 결과인지는 android/tools/check-parser.js가 확인한다.
// 사용: node server/tools/simulate-phone.js [서버주소]   (기본 http://127.0.0.1:8787)
import { parseNotification } from '../../core/parser.js';

const base = (process.argv[2] ?? 'http://127.0.0.1:8787').replace(/\/+$/, '');
const won = n => `${Number(n).toLocaleString('ko-KR')}원`;
let token = null;

async function call(method, path, body) {
  const res = await fetch(base + path, {
    method,
    headers: { 'content-type': 'application/json', ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const json = await res.json();
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${json.error}`);
  return json;
}

const stamp = () => { const d = new Date(); const p = n => String(n).padStart(2, '0'); return `${p(d.getMonth() + 1)}/${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`; };

async function phoneReceives(label, text) {
  const now = new Date();
  const parsed = parseNotification(text, { now });
  console.log(`\n[폰] ${label}`);
  if (!parsed) { console.log('  카드 승인·취소 알림이 아님 → 서버로 보내지 않음'); return null; }
  const r = await call('POST', '/api/events', { ...parsed, receivedAt: now.toISOString() });
  console.log(`  보낸 필드: ${JSON.stringify({ type: parsed.type, issuer: parsed.issuer, last4: parsed.last4, amount: parsed.amount, merchantRaw: parsed.merchantRaw })}`);
  if (r.recommendation) {
    const x = r.recommendation;
    console.log(`  [서버→폰 알림] ${won(x.delta)} 더 아낄 수 있어요 · ${x.best.membership ? x.best.membership.name + ' + ' : ''}${x.best.cardName}(${x.best.cardLast4}) · 수수료 ${won(x.fee)} · 순절약 ${won(x.net)}`);
  } else console.log(`  추천 없음${r.reason ? ` (${r.reason})` : ''}`);
  for (const u of r.updates) console.log(`  [판정 갱신] #${u.id} ${u.store} → ${u.status} (인정 차액 ${won(u.delta)})`);
  return r;
}

console.log(`서버: ${base}`);
await call('GET', '/api/health');
({ token } = await call('POST', '/api/register', { invite: process.env.RECATCH_INVITE }));
console.log('등록 완료 (토큰 발급)');

const r1 = await phoneReceives('현대카드 스타벅스 승인', `[현대카드]승인\n홍*동\n13,000원 일시불\n${stamp()}\n스타벅스영남대정\n누적350,000원`);
await phoneReceives('현대카드 취소', `[현대카드]취소\n홍*동\n13,000원\n${stamp()}\n스타벅스영남대정`);
await phoneReceives('KB 톡톡D로 재결제', `[Web발신]\nKB국민체크(9421)\n홍*동님\n${stamp()}\n13,000원\n스타벅스영남대정 사용`);
await phoneReceives('KB 빕스 승인 (재결제 안 함)', `[Web발신]\nKB국민체크(9421)\n홍*동님\n${stamp()}\n74,000원\n빕스동대구역점 사용`);
await phoneReceives('택배 문자', '[Web발신]\n[CJ대한통운] 배송 완료되었습니다.');

const confirmed = await call('POST', `/api/recommendations/${r1.recommendation.id}/confirm`, { confirmedLater: 2600 });
console.log(`\n[폰] 캐시백 2,600원 확인 → #${confirmed.id} ${confirmed.status}`);

const list = await call('GET', '/api/recommendations');
console.log('\n[폰 화면] 추천 내역');
for (const x of list) console.log(`  #${x.id} ${x.store} ${won(x.amount)} · ${x.status} · 더 아낌 ${won(x.delta)} · 수수료 ${won(x.fee)}`);
const st = await call('GET', '/api/statement');
console.log(`\n[폰 화면] ${st.month} 정산: 되찾은 돈 ${won(st.recovered)} · 수수료 ${won(st.fee)} · 첫 달 쿠폰 -${won(st.coupon)} · 청구 ${won(st.charge)} · 순절약 ${won(st.net)}`);
