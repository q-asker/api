// =============================================================
// gen-seed-ids.mjs — k6 부하용 hashid 목록 생성기 (load.js의 seed-ids.txt 입력 생성)
//
// 시드 세트의 raw id(1000000~)를 앱과 동일한 hashids 설정으로 인코딩해 한 줄당 하나씩 출력한다.
// 앱은 path variable을 hashUtil.decode()로 풀어 raw id를 얻으므로, 부하 URL에는 raw id가 아니라
// 이 인코딩된 문자열을 써야 한다(HashUtil.java / HashIdConfig.java 기준: Hashids(salt, minLength)).
//
// salt는 application-secrets.yml에 Jasypt ENC(...)로 저장돼 있다. 로컬 실행 시 복호화된 평문 salt를
// HASHID_SALT 로 넘긴다(JASYPT_PASSWORD로 복호화한 값). min-length는 app-common.yml 기준 8.
//
// 사전: npm i hashids   (perf-seed 디렉터리에서 1회)
// 실행:
//   HASHID_SALT='<복호화된 salt>' node gen-seed-ids.mjs
// 조절 env: HASHID_MIN_LENGTH(기본 8), SEED_BASE(기본 1000000), COUNT(기본 2000), OUT(기본 seed-ids.txt)
//   COUNT는 부하 대상 세트 표본 수 — scale 10 전체(41,678)를 다 쓸 필요 없이 다양성 확보용 표본이면 충분.
// =============================================================

import Hashids from 'hashids';
import { writeFileSync } from 'node:fs';

const salt = process.env.HASHID_SALT;
const minLength = parseInt(process.env.HASHID_MIN_LENGTH ?? '8', 10);
const base = parseInt(process.env.SEED_BASE ?? '1000000', 10);
const count = parseInt(process.env.COUNT ?? '2000', 10);
const out = process.env.OUT ?? 'seed-ids.txt';

if (!salt) {
  console.error('HASHID_SALT 환경변수가 필요합니다 (application-secrets.yml의 q-asker.hashid.salt 복호화 값).');
  process.exit(1);
}

const hashids = new Hashids(salt, minLength);
const lines = [];
for (let i = 0; i < count; i++) {
  lines.push(hashids.encode(base + i));
}
writeFileSync(out, lines.join('\n') + '\n');
console.log(`${count}개 hashid를 ${out}에 기록 (base=${base}, minLength=${minLength}). 예: ${lines[0]}`);
