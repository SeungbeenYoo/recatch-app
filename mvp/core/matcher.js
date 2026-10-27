// 재결제 실행 여부 판정.
// 추천 이후 들어온 승인·취소 알림을 보고, 원래 결제가 취소되고 추천 카드로 다시
// 승인됐는지 확인한다. 사용자가 "했어요"를 누르지 않아도 정산 근거가 남는다.
//
// 상태
//   waiting     안내 중 (아직 판단 불가)
//   done        취소 + 재승인 확인, 추가 절약이 모두 현장에서 확인됨 → 수수료 대상
//   pending     취소 + 재승인 확인, 캐시백·적립 확정 대기 → 확정 후 done
//   missed      시간 안에 아무 일도 없음 (수수료 없음, '놓친 혜택' 통계)
//   abandoned   취소만 하고 재결제 알림이 없음 (수수료 없음, 사용자 확인 필요)
//   double_paid 취소 없이 재승인만 확인 → 이중 결제 경고 (수수료 없음)

export const DEFAULT_MATCH = { graceSec: 600 }; // 안내 2분 + 매장 처리 여유

const sameMerchant = (a, b) =>
  a.brand && b.brand ? a.brand === b.brand && (!a.branch || !b.branch || a.branch === b.branch) : a.key === b.key;

/**
 * @param rec   recommend()의 결과 (recommend:true)
 * @param events 정규화된 알림 이벤트 [{ type, last4, amount, merchant, at }]
 */
export function matchRecapture(rec, events, { now = new Date(), graceSec = DEFAULT_MATCH.graceSec } = {}) {
  const t0 = new Date(rec.payment.at).getTime();
  const deadline = t0 + graceSec * 1000;
  const inWindow = e => { const t = new Date(e.at).getTime(); return t >= t0 && t <= deadline; };

  const cancel = events.find(e => e.type === 'cancel' && inWindow(e)
    && e.last4 === rec.payment.cardLast4 && e.amount === rec.payment.amount && sameMerchant(e.merchant, rec.payment.merchant));
  const repay = events.find(e => e.type === 'approve' && inWindow(e) && new Date(e.at).getTime() > t0
    && e.last4 === rec.best.cardLast4 && e.amount <= rec.payment.amount && sameMerchant(e.merchant, rec.payment.merchant));

  const over = now.getTime() > deadline;
  if (!cancel && !repay) return { status: over ? 'missed' : 'waiting' };
  if (cancel && !repay) return { status: over ? 'abandoned' : 'waiting', cancel };
  if (!cancel && repay) return { status: 'double_paid', repay };

  // 현장 할인은 재승인 금액 차이로 직접 확인한다. 기대보다 적게 깎였으면 그만큼만 인정.
  const observedPOS = rec.payment.amount - repay.amount;
  const posShortfall = Math.max(0, rec.best.atPOS - observedPOS);
  const realizedTotal = rec.best.total - posShortfall;
  const delta = Math.max(0, realizedTotal - rec.baseline.total);
  return {
    status: rec.best.later > 0 ? 'pending' : 'done',
    cancel,
    repay,
    observedPOS,
    awaitingConfirmation: rec.best.later,
    delta
  };
}

/** 캐시백·적립이 확정됐을 때 pending → done. confirmedLater는 실제 확정된 금액. */
export function confirmLater(result, rec, confirmedLater) {
  if (result.status !== 'pending') return result;
  const shortfall = Math.max(0, rec.best.later - confirmedLater);
  return { ...result, status: 'done', awaitingConfirmation: 0, delta: Math.max(0, result.delta - shortfall) };
}
