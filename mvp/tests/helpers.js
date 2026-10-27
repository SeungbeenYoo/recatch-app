import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const data = name => JSON.parse(readFileSync(fileURLToPath(new URL(`../data/${name}`, import.meta.url)), 'utf8'));

export const merchants = () => data('merchants.json').merchants;
export const products = () => data('products.json');
// 테스트마다 실적·사용량이 바뀌므로 매번 새로 읽는다.
export const wallet = () => data('wallet.sample.json');
export const samples = () => data('notification-samples.json').samples;

// 시연 기준 시각: 2026-10-16 (금)
export const at = (hm, day = 16) => new Date(2026, 9, day, ...hm.split(':').map(Number));
