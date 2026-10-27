// 앱(Java) 파서와 코어(JS) 파서가 같은 결과를 내는지 비교한다.
// 사용: node android/tools/check-parser.js   (mvp 폴더에서, JDK의 javac/java 필요. JAVA_HOME 우선)
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseNotification } from '../../core/parser.js';

const here = p => fileURLToPath(new URL(p, import.meta.url));
const bin = name => (process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', name) : name);

const samples = JSON.parse(readFileSync(here('../../data/notification-samples.json'), 'utf8')).samples.map(s => s.text);
// 샘플 외 경계 사례
samples.push(
  '신한카드(1083)승인 홍*동 6,500원 12/31 23:59 GS25영남대정문',
  '삼성카드 ****1234 승인 1,200원 10/16 09:00 CU경산점',
  '롯데카드(7777)승인취소 9,000원 10/16 10:00 롯데리아영남대',
  '[Web발신]\n우리카드(4321) 3개월 120,000원 10/16 13:13 올리브영 잔액 50,000원',
  '광고: 이번 주 할인 행사 50% 결제 시',
  // 앱의 재결제 체험(Demo.java)이 만드는 형식
  '[Web발신]\nKB국민카드(9421)승인 김*캐 74,000원 일시불 10/16 19:44 빕스동대구역점',
  '[Web발신]\n현대카드(5530)취소 김*캐 18,000원 10/16 08:11 파리바게뜨경산영대',
  '[Web발신]\nNH농협카드(7001)승인 김*캐 9,800원 일시불 10/16 12:20 맘스터치영남대점',
  ''
);
const NOW = '2026-10-16T12:31';

const out = mkdtempSync(join(tmpdir(), 'recatch-parser-'));
try {
  execFileSync(bin('javac'), ['-encoding', 'UTF-8', '-d', out, here('../app/src/main/java/kr/recatch/proto/CardParser.java'), here('./ParserCli.java')], { stdio: 'inherit' });
  const input = samples.map(t => t.replace(/\n/g, '\\n')).join('\n') + '\n';
  const lines = execFileSync(bin('java'), ['-Dfile.encoding=UTF-8', '-cp', out, 'ParserCli', NOW], { input, encoding: 'utf8' }).trimEnd().split(/\r?\n/);

  const [y, mo, d, h, mi] = NOW.split(/[-T:]/).map(Number);
  let fail = 0;
  samples.forEach((text, i) => {
    const js = parseNotification(text, { now: new Date(y, mo - 1, d, h, mi) });
    const jsLine = js ? [js.type, js.issuer, js.last4, js.amount, js.merchantRaw, js.at, js.installment, js.cumulative].map(v => v ?? '') : 'NULL';
    const javaLine = lines[i] === 'NULL' ? 'NULL' : lines[i].split('\t');
    // 시각은 표기(밀리초 유무)만 다를 수 있어 값으로 비교
    const norm = l => l === 'NULL' ? l : l.map((v, k) => k === 5 && v ? String(Date.parse(v)) : String(v)).join(' | ');
    const same = norm(jsLine) === norm(javaLine);
    if (!same) fail++;
    console.log(`${same ? 'OK  ' : 'DIFF'} ${JSON.stringify(text).slice(0, 60)}`);
    if (!same) console.log(`     js  : ${norm(jsLine)}\n     java: ${norm(javaLine)}`);
  });
  console.log(fail ? `\n불일치 ${fail}건` : `\n${samples.length}건 모두 일치`);
  process.exitCode = fail ? 1 : 0;
} finally {
  rmSync(out, { recursive: true, force: true });
}
