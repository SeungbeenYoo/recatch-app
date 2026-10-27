// 성과 수수료 정산.
// - 완료(done) 건의 "기준선 대비 추가 절약액"에만 수수료를 매긴다. 확인 중·놓친 건은 0원.
// - 첫 달 쿠폰은 실제 수수료를 넘지 않는다.
// - 프리미엄 구독자는 수수료 대신 월 구독료만 낸다.
// - 실제 청구는 PG 정기결제로 월말 1회 (자금을 직접 보관하지 않는다).

export const FEE_RATE = 0.2;
export const PREMIUM_MONTHLY = 2900;
export const FIRST_MONTH_COUPON = 1000;

// 웹 시연(index.html)의 feeFor와 같은 반올림을 쓴다.
export const feeFor = (delta, rate = FEE_RATE) => Math.round(Math.max(0, delta) * rate);

/**
 * @param recaptures [{ id, status:'done'|'pending'|'missed'|..., delta, merchant, at }]
 * @param opts { premium?:boolean, firstMonth?:boolean, feeRate?:number }
 */
export function monthlyStatement(recaptures, { premium = false, firstMonth = false, feeRate = FEE_RATE } = {}) {
  const items = recaptures.map(r => {
    const charged = r.status === 'done' && !premium;
    return { id: r.id, status: r.status, delta: r.delta, fee: charged ? feeFor(r.delta, feeRate) : 0 };
  });
  const done = items.filter(i => i.status === 'done');
  const recovered = done.reduce((a, i) => a + i.delta, 0);
  const fee = items.reduce((a, i) => a + i.fee, 0);
  const coupon = firstMonth ? Math.min(FIRST_MONTH_COUPON, fee) : 0;
  const subscription = premium ? PREMIUM_MONTHLY : 0;
  const charge = fee - coupon + subscription;
  return {
    items,
    recovered,
    fee,
    coupon,
    subscription,
    charge,
    net: recovered - charge,
    pending: recaptures.filter(r => r.status === 'pending').reduce((a, r) => a + r.delta, 0),
    missed: recaptures.filter(r => r.status === 'missed').reduce((a, r) => a + (r.delta ?? 0), 0)
  };
}

// 소액 청구 묶기: 누적 청구액이 기준(1만 원) 이상인 달에 한 번에 청구하고, 미만이면 다음 달로 넘긴다.
export const BILLING_THRESHOLD = 10000;

/** @param charges [{ month, charge }] 오래된 달부터 */
export function billingCycle(charges, threshold = BILLING_THRESHOLD) {
  let carry = 0;
  return charges.map(({ month, charge }) => {
    const totalDue = carry + charge;
    const billed = totalDue >= threshold;
    const row = { month, charge, carriedIn: carry, totalDue, billed, carriedOut: billed ? 0 : totalDue };
    carry = row.carriedOut;
    return row;
  });
}
