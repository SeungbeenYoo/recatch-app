// 가맹점명 정규화 → 브랜드·업종 매핑.
// 승인 문자의 가맹점명은 잘리거나 지점명이 붙어 온다 (예: "스타벅스영남대정").
// merchants 데이터: [{ brand, category, aliases: [...] }]

const squash = s => String(s ?? '').normalize('NFC').replace(/[\s()㈜\-_.·]/g, '').replace(/주식회사|\(주\)/g, '').toUpperCase();

export function buildMerchantIndex(merchants) {
  const entries = [];
  for (const m of merchants) {
    for (const name of [m.brand, ...(m.aliases ?? [])]) {
      entries.push({ key: squash(name), brand: m.brand, category: m.category });
    }
  }
  // 긴 별칭을 먼저 비교해야 "GS25"보다 구체적인 이름이 이긴다.
  entries.sort((a, b) => b.key.length - a.key.length);
  return entries;
}

/**
 * @returns {{brand:string|null, category:string|null, branch:string|null, key:string}}
 */
export function normalizeMerchant(raw, index) {
  const key = squash(raw);
  const hit = index.find(e => key.startsWith(e.key)) ?? index.find(e => e.key.length >= 3 && key.includes(e.key));
  if (!hit) return { brand: null, category: null, branch: null, key };
  const at = key.indexOf(hit.key);
  const branch = key.slice(at + hit.key.length) || null;
  return { brand: hit.brand, category: hit.category, branch, key };
}
