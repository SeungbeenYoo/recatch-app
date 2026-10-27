// 실행: node server/server.js   (mvp 폴더에서 `npm start`)
// 환경 변수
//   PORT            기본 8787
//   HOST            기본 0.0.0.0 (같은 Wi-Fi의 폰에서 접속). PC 안에서만 쓰려면 127.0.0.1
//   RECATCH_DB      저장 파일, 기본 mvp/server/.data/db.json
//   RECATCH_INVITE  설정하면 등록할 때 이 초대 코드가 필요

import { createServer } from 'node:http';
import { networkInterfaces } from 'node:os';
import { fileURLToPath } from 'node:url';
import { createApp } from './app.js';
import { createFileStore } from './store.js';
import { loadCatalog } from './catalog.js';

const PORT = Number(process.env.PORT ?? 8787);
const HOST = process.env.HOST ?? '0.0.0.0';
const DB = process.env.RECATCH_DB ?? fileURLToPath(new URL('./.data/db.json', import.meta.url));

const store = createFileStore(DB);
const handle = createApp({ store, catalog: loadCatalog(), invite: process.env.RECATCH_INVITE || null });
const server = createServer((req, res) => {
  const t0 = Date.now();
  res.on('finish', () => console.log(`${new Date().toLocaleTimeString('ko-KR')} ${req.method} ${req.url.split('?')[0]} ${res.statusCode} ${Date.now() - t0}ms`));
  handle(req, res);
});

const ticker = setInterval(handle.tickAll, 10_000);

server.listen(PORT, HOST, () => {
  console.log(`리캐치 백엔드 시제품: http://${HOST === '0.0.0.0' ? 'localhost' : HOST}:${PORT}  (저장: ${DB})`);
  if (HOST === '0.0.0.0') {
    const ips = Object.values(networkInterfaces()).flat().filter(i => i && i.family === 'IPv4' && !i.internal).map(i => i.address);
    for (const ip of ips) console.log(`  폰 앱 서버 주소 후보: http://${ip}:${PORT}`);
  }
});

function shutdown() {
  clearInterval(ticker);
  store.flush();
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(0), 1000).unref();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
