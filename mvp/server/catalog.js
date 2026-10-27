import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const load = name => JSON.parse(readFileSync(fileURLToPath(new URL(`../data/${name}`, import.meta.url)), 'utf8'));

// 혜택 룰·가맹점 사전은 서버가 들고 있다 (앱 업데이트 없이 룰을 고칠 수 있게).
// 새 사용자의 지갑은 시제품이라 예시 지갑으로 시작하고, 앱/API에서 바꾼다.
export function loadCatalog() {
  const merchants = load('merchants.json');
  const products = load('products.json');
  const defaultWallet = load('wallet.sample.json');
  delete defaultWallet._note;
  return { merchants: merchants.merchants, categories: merchants.categories, products, defaultWallet };
}
