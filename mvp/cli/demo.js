// 사용법
//   node cli/demo.js                 시연 시나리오 전체 (승인 → 추천 → 취소·재승인 → 월 정산)
//   node cli/demo.js "<알림 원문>"    문자 1건을 넣어 파싱·추천 결과 보기
// Windows·Linux 모두 같은 명령으로 동작한다.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { createReCatch, parseNotification, monthlyStatement } from '../core/index.js';

const load = name => JSON.parse(readFileSync(fileURLToPath(new URL(`../data/${name}`, import.meta.url)), 'utf8'));
const won = n => `${Math.round(n).toLocaleString('ko-KR')}원`;
const app = () => createReCatch({ merchants: load('merchants.json').merchants, products: load('products.json'), wallet: load('wallet.sample.json') });

function show(rec) {
  console.log(`  ▶ 재결제 추천: ${rec.best.cardName}(${rec.best.cardLast4})${rec.best.membership ? ` + ${rec.best.membership.name}` : ''}`);
  console.log(`    지금 ${won(rec.baseline.total)} → 재결제 시 ${won(rec.best.total)}  (더 아낌 ${won(rec.delta)}, 수수료 ${won(rec.fee)}, 순절약 ${won(rec.net)})`);
  console.log(`    마감 ${new Date(rec.expiresAt).toLocaleTimeString('ko-KR')}`);
  rec.steps.forEach((s, i) => console.log(`    ${i + 1}. ${s}`));
  rec.warnings.forEach(w => console.log(`    ⚠ ${w.message}`));
}

const arg = process.argv.slice(2).join(' ').replace(/\\n/g, '\n');
if (arg) {
  // 넣은 문자를 "방금 받은 것"으로 보고 판단한다 (문자 속 승인 시각 + 30초).
  const parsed = parseNotification(arg);
  console.log('파싱 결과:', parsed);
  const now = parsed?.at ? new Date(new Date(parsed.at).getTime() + 30000) : new Date();
  const r = app().onNotification(arg, { now });
  if (r.ignored) console.log('카드 승인·취소 알림이 아니라 무시합니다.');
  else if (r.recommendation) show(r.recommendation);
  else console.log('추천 없음 (더 큰 혜택이 없거나, 2분이 지났거나, 가맹점·카드를 모름)');
  process.exit(0);
}

// 시연 시나리오: 2026-10-16
const T = (hm) => new Date(2026, 9, 16, ...hm.split(':').map(Number));
const rc = app();
const script = [
  ['08:10', '현대카드 승인 18,000원 일시불 10/16 08:10 파리바게뜨경산영대'],
  ['08:11', '현대카드 취소 18,000원 10/16 08:11 파리바게뜨경산영대'],
  ['08:11', '현대카드 승인 16,200원 일시불 10/16 08:11 파리바게뜨경산영대'],
  ['12:30', '[현대카드]승인\n홍*동\n13,000원 일시불\n10/16 12:30\n스타벅스영남대정\n누적350,000원'],
  ['12:31', '[현대카드]취소\n홍*동\n13,000원\n10/16 12:31\n스타벅스영남대정'],
  ['12:31', '[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 12:31\n13,000원\n스타벅스영남대정 사용'],
  ['19:44', '[Web발신]\nKB국민체크(9421)\n홍*동님\n10/16 19:44\n74,000원\n빕스동대구역점 사용'],
  ['22:41', '[Web발신]\n신한카드(1083)승인 홍*동 6,500원(일시불)10/16 22:41 GS25영남대정문 누적268,000원']
];
for (const [hm, text] of script) {
  const r = rc.onNotification(text, { now: T(hm) });
  const e = r.event;
  console.log(`\n[${hm}] ${e.type === 'cancel' ? '취소' : '승인'} ${won(e.amount)} · ${e.merchant.brand ?? e.merchant.key} · 카드 ${e.last4}`);
  if (r.recommendation) show(r.recommendation);
  for (const u of r.updates) console.log(`  ✔ 추천 #${u.id} → ${u.status}${u.delta != null ? ` (인정 차액 ${won(u.delta)})` : ''}`);
}
for (const u of rc.tick({ now: T('23:59') })) console.log(`\n[마감] 추천 #${u.id} → ${u.status}`);

// 카드사 확정 반영 (예: 0.7% 청구할인 113원, 스타벅스 캐시백 2,600원)
rc.confirm(1, 113);
rc.confirm(2, 2600);

const records = rc.open.map(o => rc.toServerRecord(o.id));
console.log('\n서버로 보내는 기록 (원문·카드번호·지점명 없음):');
console.table(records);
const s = monthlyStatement(records, { firstMonth: true });
console.log(`월 정산: 되찾은 돈 ${won(s.recovered)} · 수수료 ${won(s.fee)} · 첫 달 쿠폰 -${won(s.coupon)} · 청구 ${won(s.charge)} · 순절약 ${won(s.net)} · 놓친 혜택 ${won(s.missed)}`);
