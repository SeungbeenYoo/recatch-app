import test from 'node:test';
import assert from 'node:assert/strict';
import { parseNotification, buildMerchantIndex, normalizeMerchant } from '../core/index.js';
import { samples, merchants, at } from './helpers.js';

const now = at('12:31');

for (const s of samples()) {
  test(`파서: ${s.id}`, () => {
    const r = parseNotification(s.text, { now });
    if (s.expect === null) return assert.equal(r, null);
    for (const [k, v] of Object.entries(s.expect)) assert.deepEqual(r[k], v, k);
  });
}

test('파서 결과에 이름·원문이 남지 않는다', () => {
  for (const s of samples()) {
    const r = parseNotification(s.text, { now });
    if (!r) continue;
    const out = JSON.stringify(r);
    assert.ok(!out.includes('홍'), s.id);
    assert.ok(!out.includes('Web발신'), s.id);
  }
});

test('승인 시각은 문자 속 월/일 시:분으로 정한다', () => {
  const r = parseNotification(samples()[0].text, { now });
  assert.equal(new Date(r.at).getTime(), at('12:30').getTime());
});

test('1월에 받은 12월 거래는 작년으로 본다', () => {
  const r = parseNotification('신한카드(1083)승인 홍*동 6,500원 12/31 23:59 GS25영남대정문', { now: new Date(2027, 0, 1, 0, 1) });
  assert.equal(new Date(r.at).getFullYear(), 2026);
});

test('가맹점 정규화: 지점명 분리와 별칭', () => {
  const idx = buildMerchantIndex(merchants());
  assert.deepEqual(
    (({ brand, category, branch }) => ({ brand, category, branch }))(normalizeMerchant('스타벅스영남대정', idx)),
    { brand: '스타벅스', category: 'cafe', branch: '영남대정' });
  assert.equal(normalizeMerchant('씨제이올리브영 경산중앙', idx).brand, '올리브영');
  assert.equal(normalizeMerchant('gs25 영남대정문', idx).brand, 'GS25');
  assert.equal(normalizeMerchant('동네분식', idx).brand, null);
});
