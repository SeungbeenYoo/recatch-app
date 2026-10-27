// 카드 승인·취소 알림(문자/앱 푸시) 파서.
// 단말 안에서만 실행하고, 결과에는 판단에 필요한 최소 필드만 남긴다.
// 원문·카드 소유자 이름·전체 카드번호는 반환하지 않는다.
//
// 주의: 아래 발급사별 형식은 공개된 예시를 바탕으로 한 추정이다.
// 팀원 실제 승인 문자(이름 가림)를 data/notification-samples.json에 모아 보정해야 한다.

const ISSUERS = [
  { id: 'kb', pattern: /KB국민|국민카드|KB카드/ },
  { id: 'sh', pattern: /신한/ },
  { id: 'hd', pattern: /현대카드/ },
  { id: 'ss', pattern: /삼성카드/ },
  { id: 'lt', pattern: /롯데카드/ },
  { id: 'hn', pattern: /하나카드/ },
  { id: 'wr', pattern: /우리카드/ },
  { id: 'nh', pattern: /NH농협|농협카드/ },
  { id: 'bc', pattern: /BC카드|비씨카드/ }
];

const AMOUNT = /(\d{1,3}(?:,\d{3})+|\d+)\s*원/;
const LAST4 = /\((\d{4})\)|(\d{4})\s*\*{0,4}\s*(?=승인|취소|사용)|\*{2,}(\d{4})/;
const DATETIME = /(\d{1,2})\/(\d{1,2})\s+(\d{1,2}):(\d{2})/;
const CUMULATIVE = /누적\s*:?\s*(\d{1,3}(?:,\d{3})+|\d+)\s*원/;
const INSTALLMENT = /(일시불|\d{1,2}\s*개월)/;

const toNumber = s => Number(String(s).replace(/,/g, ''));

// 가맹점 후보에서 빼야 하는 줄·토큰
const NOISE = [
  /(KB국민|국민|신한|현대|삼성|롯데|하나|우리|NH농협|농협|BC|비씨)(카드|체크|신용)[가-힣A-Za-z0-9]*/g, /\[[^\]]*\]/g, /승인|취소|사용|결제/g, /일시불|\d{1,2}\s*개월/g,
  /누적\s*:?\s*[\d,]+\s*원/g, /잔액\s*:?\s*[\d,]+\s*원/g, /[\d,]+\s*원/g,
  /\d{1,2}\/\d{1,2}\s+\d{1,2}:\d{2}/g, /\(\d{4}\)/g,
  /[가-힣A-Za-z]\*[가-힣A-Za-z]*님?/g, // 가려진 이름: 홍*동, 홍*동님
  /[가-힣]{2,4}님/g,
  /[()]/g
];

function detectType(text) {
  if (/승인\s*취소|취소/.test(text)) return 'cancel';
  if (/승인|사용|결제/.test(text)) return 'approve';
  return null;
}

function detectIssuer(text) {
  const hit = ISSUERS.find(i => i.pattern.test(text));
  return hit ? hit.id : null;
}

function pickMerchant(text) {
  const lines = text.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
  const candidates = [];
  for (const line of lines) {
    if (ISSUERS.some(i => i.pattern.test(line)) && !/\d{1,2}:\d{2}.+\S/.test(line)) continue;
    let rest = line;
    for (const n of NOISE) rest = rest.replace(n, ' ');
    rest = rest.replace(/\s+/g, ' ').trim();
    if (rest.length >= 2 && /[가-힣A-Za-z]/.test(rest)) candidates.push(rest);
  }
  // 한 줄짜리 문자는 마지막 덩어리가 가맹점인 경우가 많다.
  return candidates.length ? candidates[candidates.length - 1] : null;
}

/**
 * @param {string} text 알림 원문
 * @param {{now?: Date}} [opts] 연도 보정용 기준 시각
 * @returns {null | {type:'approve'|'cancel', issuer:string|null, last4:string|null,
 *   amount:number, merchantRaw:string|null, at:string|null, installment:string|null,
 *   cumulative:number|null}}
 */
export function parseNotification(text, opts = {}) {
  if (typeof text !== 'string' || !text.trim()) return null;
  const type = detectType(text);
  // 누적·잔액 금액이 결제 금액보다 앞에 오는 형식도 있으므로 먼저 지운다.
  const amountM = text.replace(/(누적|잔액)\s*:?\s*[\d,]+\s*원/g, '').match(AMOUNT);
  if (!type || !amountM) return null;

  const issuer = detectIssuer(text);
  if (!issuer) return null; // 카드 승인 알림이 아닌 메시지는 무시

  const l4 = text.match(LAST4);
  const dt = text.match(DATETIME);
  const now = opts.now ?? new Date();
  let at = null;
  if (dt) {
    const [, mo, d, h, mi] = dt.map(Number);
    let year = now.getFullYear();
    // 1월 초에 받은 12월 거래 문자
    if (mo > now.getMonth() + 1 + 1) year -= 1;
    at = new Date(year, mo - 1, d, h, mi).toISOString();
  }
  const cum = text.match(CUMULATIVE);
  const inst = text.match(INSTALLMENT);

  return {
    type,
    issuer,
    last4: l4 ? (l4[1] || l4[2] || l4[3]) : null,
    amount: toNumber(amountM[1]),
    merchantRaw: pickMerchant(text),
    at,
    installment: inst ? inst[1].replace(/\s+/g, '') : null,
    cumulative: cum ? toNumber(cum[1]) : null
  };
}
