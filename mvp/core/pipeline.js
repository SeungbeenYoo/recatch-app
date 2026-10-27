// 전체 흐름: 알림 → 파싱 → 가맹점 정규화 → 추천 → 재결제 판정.
// - 단말 단독 실행: onNotification(원문)
// - 백엔드 실행: 단말이 파싱한 필드를 onParsed()로 넣는다 (서버는 원문을 받지 않는다)
// 상태(state)는 JSON으로 직렬화할 수 있어서 서버가 사용자별로 저장·복원한다.

import { parseNotification } from './parser.js';
import { buildMerchantIndex, normalizeMerchant } from './merchant.js';
import { recommend, cardBenefitFor } from './engine.js';
import { matchRecapture, confirmLater } from './matcher.js';

// 정산 날짜는 현지 날짜로 (toISOString은 UTC라 한국 오전 9시 전 결제가 전날이 된다)
export const localDate = iso => {
  const d = new Date(iso);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const EVENT_KEEP_MS = 24 * 3600 * 1000; // 재결제 판정에 필요한 만큼만 보관

export const emptyState = () => ({ events: [], open: [], nextId: 1 });

export function createReCatch({ merchants, products, wallet, state = emptyState(), config = {}, match = {} }) {
  const index = buildMerchantIndex(merchants);
  const ctx = { products, wallet };
  const { events, open } = state;

  function onNotification(text, { now = new Date() } = {}) {
    const parsed = parseNotification(text, { now });
    if (!parsed) return { ignored: true };
    return onParsed(parsed, { now });
  }

  /**
   * @param parsed parseNotification()과 같은 형태 { type, issuer, last4, amount, merchantRaw, at, cumulative }
   * @param at     이벤트 시각을 따로 정할 때 (단말이 알림을 받은 정확한 시각)
   */
  function onParsed(parsed, { now = new Date(), at = null } = {}) {
    // 끝자리가 없는 형식은 같은 카드사 카드가 지갑에 1장뿐일 때만 그 카드로 본다.
    let last4 = parsed.last4;
    if (!last4) {
      const same = wallet.cards.filter(c => c.issuer === parsed.issuer);
      if (same.length === 1) last4 = same[0].last4;
    }
    const event = {
      type: parsed.type,
      last4,
      amount: parsed.amount,
      merchant: normalizeMerchant(parsed.merchantRaw, index),
      at: at ?? parsed.at ?? now.toISOString()
    };
    rollMonth(localDate(event.at).slice(0, 7));
    events.push(event);
    prune(now);

    // 실적 추정: 문자에 누적 금액이 있으면 그 값을, 없으면 승인·취소로 가감한다.
    const card = wallet.cards.find(c => c.last4 === event.last4);
    if (card) {
      if (parsed.cumulative != null) card.monthSpend = parsed.cumulative;
      else if (card.monthSpend != null) card.monthSpend += event.type === 'cancel' ? -event.amount : event.amount;
    }

    let recommendation = null;
    let reason = null;
    if (event.type === 'approve' && !isRepayOfOpen(event)) {
      // 실적 차감 경고는 이 결제가 반영된 누적 실적을 기준으로 계산한다.
      const r = recommend({ ...event, cardLast4: event.last4 }, ctx, { now, config });
      if (r.recommend) {
        recommendation = { id: state.nextId++, ...r };
        open.push({ id: recommendation.id, rec: recommendation, result: { status: 'waiting' } });
      } else reason = r.reason;
    }
    trackUsage(event, card);
    return { event, recommendation, reason, updates: tick({ now }) };
  }

  // 달이 바뀌면 이번 달 사용액이 전월 실적이 되고, 혜택 한도 사용액은 0부터 다시 센다.
  function rollMonth(month) {
    if (wallet.month && wallet.month >= month) return;
    if (wallet.month) {
      for (const c of wallet.cards) {
        if (c.monthSpend != null) c.prevMonthSpend = Math.max(0, c.monthSpend);
        c.monthSpend = 0;
        c.usage = {};
      }
      for (const m of wallet.memberships ?? []) m.usage = {};
    }
    wallet.month = month;
  }

  // 혜택 월 한도: 승인되면 그 카드 혜택 사용액을 더하고, 같은 결제가 취소되면 뺀다.
  // (추천 계산이 끝난 뒤에 반영해야 원래 결제의 기준선이 이 결제를 두 번 세지 않는다)
  function trackUsage(event, card) {
    if (!card) return;
    card.usage ??= {};
    if (event.type === 'approve') {
      const u = cardBenefitFor({ ...event, cardLast4: event.last4 }, ctx);
      if (!u) return;
      card.usage[u.benefitId] = (card.usage[u.benefitId] ?? 0) + u.value;
      event.usage = u;
    } else {
      const key = m => m.brand ?? m.key;
      for (let i = events.length - 2; i >= 0; i--) {
        const e = events[i];
        if (e.type === 'approve' && e.usage && !e.cancelled && e.last4 === event.last4 && e.amount === event.amount && key(e.merchant) === key(event.merchant)) {
          card.usage[e.usage.benefitId] = Math.max(0, (card.usage[e.usage.benefitId] ?? 0) - e.usage.value);
          e.cancelled = true;
          return;
        }
      }
    }
  }

  function prune(now) {
    const cutoff = now.getTime() - EVENT_KEEP_MS;
    for (let i = events.length - 1; i >= 0; i--) if (new Date(events[i].at).getTime() < cutoff) events.splice(i, 1);
  }

  // 이미 안내 중인 건의 재결제 승인이면 새 추천을 만들지 않는다.
  function isRepayOfOpen(event) {
    return open.some(o => o.result.status === 'waiting' && o.rec.best.cardLast4 === event.last4
      && (o.rec.payment.merchant.brand ?? o.rec.payment.merchant.key) === (event.merchant.brand ?? event.merchant.key));
  }

  function tick({ now = new Date() } = {}) {
    const changed = [];
    for (const o of open) {
      if (o.result.status !== 'waiting') continue;
      const r = matchRecapture(o.rec, events, { now, ...match });
      if (r.status !== 'waiting') { o.result = r; changed.push({ id: o.id, ...r }); }
    }
    return changed;
  }

  function confirm(id, confirmedLater) {
    const o = open.find(x => x.id === id);
    if (!o) return null;
    o.result = confirmLater(o.result, o.rec, confirmedLater);
    return o.result;
  }

  // "이미 멤버십을 썼어요": 기준선을 다시 잡아 추천을 고친다. 더 아낄 게 없으면 no_gain으로 닫는다.
  function rebaseline(id, membershipId, { now = new Date() } = {}) {
    const o = open.find(x => x.id === id);
    if (!o || o.result.status !== 'waiting') return null;
    const p = o.rec.payment;
    const r = recommend({ ...p }, ctx, { now, config, baselineMembershipId: membershipId });
    if (r.recommend) o.rec = { id: o.id, ...r };
    else o.result = { status: r.reason === 'window_passed' ? 'missed' : 'no_gain', delta: 0 };
    return o;
  }

  // 정산용 최소 기록: 날짜·브랜드·업종·상태·차액.
  function toServerRecord(id) {
    const o = open.find(x => x.id === id);
    if (!o) return null;
    return {
      id: o.id,
      date: localDate(o.rec.payment.at),
      brand: o.rec.payment.merchant.brand,
      category: o.rec.payment.merchant.category,
      status: o.result.status,
      delta: o.result.delta ?? o.rec.delta
    };
  }

  return { onNotification, onParsed, tick, confirm, rebaseline, toServerRecord, state, get open() { return open; } };
}
