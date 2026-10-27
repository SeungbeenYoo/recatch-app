// 혜택 룰 엔진: 결제 1건에 대해 보유 카드 × 멤버십 조합의 혜택을 계산하고
// 원래 결제(기준선)보다 더 아낄 수 있는 재결제 방법을 고른다.
//
// 금액 단위는 원, rate는 퍼센트(20 = 20%).
// 수수료는 기준선 대비 "추가로 아낀 금액(delta)"에만 매긴다 (README 한계점 4).

import { feeFor } from './settlement.js';

export const DEFAULT_CONFIG = {
  windowSec: 120,     // 결제 후 재결제 안내 유효 시간 (기획서: 2분)
  minDelta: 1000,     // 이보다 적게 더 아끼면 알리지 않는다 (알림 피로 방지)
  feeRate: 0.2
};

const DAY = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];

function inTimeRange(at, range) {
  if (!range) return true;
  const d = new Date(at);
  const hm = d.getHours() * 60 + d.getMinutes();
  const [s, e] = range.map(t => { const [h, m] = t.split(':').map(Number); return h * 60 + m; });
  return s <= e ? hm >= s && hm <= e : hm >= s || hm <= e;
}

function matches(benefit, payment) {
  const m = benefit.match ?? {};
  const { brand, category } = payment.merchant;
  if (benefit.excludeBrands?.includes(brand)) return false;
  if (benefit.minAmount && payment.amount < benefit.minAmount) return false;
  if (benefit.days && payment.at && !benefit.days.includes(DAY[new Date(payment.at).getDay()])) return false;
  if (benefit.timeRange && payment.at && !inTimeRange(payment.at, benefit.timeRange)) return false;
  if (m.all) return true;
  if (m.brands?.includes(brand)) return true;
  if (m.categories?.includes(category)) return true;
  return false;
}

// 한 혜택이 이 금액에서 주는 가치. 한도와 이번 달 사용량을 반영한다.
function benefitValue(benefit, amount, used = 0) {
  let v = benefit.fixed ?? Math.floor(amount * benefit.rate / 100);
  if (benefit.type === 'points') v = Math.floor(v * (benefit.pointValue ?? 1));
  if (benefit.perTxCap != null) v = Math.min(v, benefit.perTxCap);
  if (benefit.monthlyCap != null) v = Math.min(v, Math.max(0, benefit.monthlyCap - used));
  return Math.max(0, v);
}

function bestBenefit(benefits, payment, amount, usage = {}) {
  let best = null;
  for (const b of benefits) {
    if (!matches(b, payment)) continue;
    const value = benefitValue(b, amount, usage[b.id] ?? 0);
    if (value > 0 && (!best || value > best.value)) best = { benefit: b, value };
  }
  return best;
}

function evaluateCard(product, walletCard, payment, payAmount) {
  if (product.prevMonthMin && (walletCard.prevMonthSpend ?? 0) < product.prevMonthMin) {
    return { value: 0, reason: 'prev_month_unmet' };
  }
  const hit = bestBenefit(product.benefits, payment, payAmount, walletCard.usage);
  if (!hit) return { value: 0, reason: 'no_match' };
  return { value: hit.value, benefitId: hit.benefit.id, label: hit.benefit.label, type: hit.benefit.type, atPOS: hit.benefit.atPOS === true };
}

/**
 * 이 카드로 이 결제를 했을 때 실제로 쓰이는 카드 혜택 (월 한도 사용액을 쌓기 위해).
 * 멤버십 현장할인이 있었는지는 알 수 없으므로 결제 금액 그대로 계산한다.
 * @returns {{ benefitId, value } | null}
 */
export function cardBenefitFor(payment, ctx) {
  const wc = ctx.wallet.cards.find(c => c.last4 === payment.cardLast4);
  const product = wc && ctx.products.cards.find(p => p.id === wc.productId);
  if (!product) return null;
  const r = evaluateCard(product, wc, payment, payment.amount);
  return r.value > 0 ? { benefitId: r.benefitId, value: r.value } : null;
}

function evaluateMembership(product, walletMem, payment) {
  const tier = product.tiers?.[walletMem.tier];
  const benefits = tier?.benefits ?? product.benefits ?? [];
  const hit = bestBenefit(benefits, payment, payment.amount, walletMem.usage);
  if (!hit) return null;
  return { value: hit.value, benefitId: hit.benefit.id, label: hit.benefit.label, type: hit.benefit.type, stackWithCard: hit.benefit.stackWithCard !== false };
}

/**
 * 가능한 모든 결제 방법(카드 1장 + 멤버십 0~1개)을 계산해 가치 순으로 돌려준다.
 * @param payment { amount, merchant:{brand,category}, at, cardLast4 }
 * @param ctx { products:{cards,memberships}, wallet:{cards,memberships} }
 */
export function enumerateOptions(payment, ctx) {
  const cardProducts = new Map(ctx.products.cards.map(p => [p.id, p]));
  const memProducts = new Map(ctx.products.memberships.map(p => [p.id, p]));
  const memOptions = [null];
  for (const wm of ctx.wallet.memberships ?? []) {
    const p = memProducts.get(wm.productId);
    if (!p) continue;
    const r = evaluateMembership(p, wm, payment);
    if (r) memOptions.push({ ...r, productId: p.id, name: p.name });
  }

  const options = [];
  for (const wc of ctx.wallet.cards) {
    const product = cardProducts.get(wc.productId);
    if (!product) continue;
    for (const mem of memOptions) {
      // 멤버십 할인(현장 할인)이면 카드는 할인된 금액으로 결제한다. 적립형은 결제액이 그대로다.
      const posDiscount = mem && mem.type === 'discount' ? mem.value : 0;
      const card = evaluateCard(product, wc, payment, payment.amount - posDiscount);
      const cardValue = mem && !mem.stackWithCard ? 0 : card.value;
      options.push({
        cardLast4: wc.last4,
        cardName: product.name,
        cardKind: product.kind,
        card: { ...card, value: cardValue },
        membership: mem,
        total: cardValue + (mem?.value ?? 0),
        // 현장 할인은 재승인 금액으로 바로 확인된다. 카드 청구할인·캐시백·적립은 나중에 확정된다.
        atPOS: posDiscount + (card.atPOS ? cardValue : 0),
        later: (mem && mem.type !== 'discount' ? mem.value : 0) + (card.atPOS ? 0 : cardValue)
      });
    }
  }
  options.sort((a, b) => b.total - a.total || (a.membership ? 1 : 0) - (b.membership ? 1 : 0));
  return options;
}

function warningsFor(payment, best, ctx) {
  const w = [];
  const orig = ctx.wallet.cards.find(c => c.last4 === payment.cardLast4);
  const origProduct = orig && ctx.products.cards.find(p => p.id === orig.productId);
  if (origProduct?.prevMonthMin && orig.monthSpend != null) {
    const before = orig.monthSpend;
    const after = before - payment.amount;
    if (before >= origProduct.prevMonthMin && after < origProduct.prevMonthMin) {
      w.push({ code: 'original_card_performance', message: `취소하면 ${origProduct.name} 이번 달 실적이 ${(origProduct.prevMonthMin - after).toLocaleString('ko-KR')}원 모자라 다음 달 혜택이 빠질 수 있어요` });
    }
  }
  if (origProduct?.kind === 'check') {
    w.push({ code: 'check_refund_delay', message: '체크카드 취소 금액은 계좌로 돌아오기까지 영업일 기준 며칠 걸릴 수 있어요' });
  }
  if (best.later > 0) {
    w.push({ code: 'confirmed_later', message: '캐시백·적립은 카드사 확정 후 반영돼요' });
  }
  return w;
}

function stepsFor(best) {
  const s = ['직원에게 "방금 결제 취소하고 다른 카드로 다시 결제할게요"라고 말해 주세요'];
  if (best.membership) s.push(`${best.membership.name} 바코드를 먼저 보여 주세요`);
  s.push(`${best.cardName}(${best.cardLast4})로 다시 결제하세요`);
  return s;
}

/**
 * 결제 1건에 대한 재결제 추천. 추천할 것이 없으면 { recommend:false, reason }.
 * @param baselineMembershipId 사용자가 "이미 멤버십을 적용했어요"라고 알려준 경우
 */
export function recommend(payment, ctx, { now = new Date(), config = {}, baselineMembershipId = null } = {}) {
  const cfg = { ...DEFAULT_CONFIG, ...config };
  if (payment.at) {
    const elapsed = (now - new Date(payment.at)) / 1000;
    if (elapsed > cfg.windowSec) return { recommend: false, reason: 'window_passed' };
  }
  if (!payment.merchant?.brand && !payment.merchant?.category) return { recommend: false, reason: 'unknown_merchant' };

  const options = enumerateOptions(payment, ctx);
  const origOptions = options.filter(o => o.cardLast4 === payment.cardLast4);
  if (!origOptions.length) return { recommend: false, reason: 'unknown_card' };
  const baseline = origOptions.find(o => (o.membership?.productId ?? null) === baselineMembershipId) ?? origOptions.find(o => !o.membership);
  const best = options[0];
  const delta = best.total - baseline.total;
  if (delta < cfg.minDelta) return { recommend: false, reason: 'below_threshold', delta, baseline, best };

  const fee = feeFor(delta, cfg.feeRate);
  const startedAt = payment.at ? new Date(payment.at) : now;
  return {
    recommend: true,
    payment: { amount: payment.amount, merchant: payment.merchant, cardLast4: payment.cardLast4, at: payment.at },
    baseline,
    best,
    delta,
    fee,
    net: delta - fee,
    expiresAt: new Date(startedAt.getTime() + cfg.windowSec * 1000).toISOString(),
    steps: stepsFor(best),
    warnings: warningsFor(payment, best, ctx)
  };
}
