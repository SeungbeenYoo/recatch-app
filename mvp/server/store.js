// 시제품용 저장소: JSON 파일 1개. 쓰기는 임시 파일 → rename으로 원자적으로 바꾼다.
// 실서비스에서는 이 인터페이스(load/save/users)만 유지한 채 DB로 교체한다.

import { readFileSync, writeFileSync, renameSync, mkdirSync, existsSync } from 'node:fs';
import { dirname } from 'node:path';

export function createFileStore(file) {
  let db = { users: {} };
  if (file && existsSync(file)) db = JSON.parse(readFileSync(file, 'utf8'));
  let timer = null;

  function flush() {
    timer = null;
    if (!file) return;
    mkdirSync(dirname(file), { recursive: true });
    const tmp = `${file}.tmp`;
    writeFileSync(tmp, JSON.stringify(db));
    renameSync(tmp, file);
  }

  return {
    get users() { return db.users; },
    // 요청이 몰려도 디스크 쓰기는 짧게 모아서 한 번
    save() { if (!timer) { timer = setTimeout(flush, 200); timer.unref?.(); } },
    flush() { if (timer) clearTimeout(timer); flush(); }
  };
}

export const memoryStore = () => createFileStore(null);
