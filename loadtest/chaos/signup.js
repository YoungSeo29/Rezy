// chaos 테스트용 사용자 N명 가입 + 토큰 수집
const N = parseInt(process.argv[2] || '300', 10);
const BASE = 'http://127.0.0.1:8080';
const fs = require('fs');

async function one(i) {
  const email = `chaos-${String(i).padStart(4,'0')}@test.com`;
  const body = JSON.stringify({ email, password: 'Passw0rd!23' });
  const h = { 'Content-Type': 'application/json' };
  let r = await fetch(`${BASE}/api/auth/signup`, { method:'POST', headers:h, body });
  if (r.status === 201) return (await r.json()).accessToken;
  // 이미 가입된 경우 로그인으로 토큰 획득
  r = await fetch(`${BASE}/api/auth/login`, { method:'POST', headers:h, body });
  if (r.ok) return (await r.json()).accessToken;
  console.error(`FAIL ${email} signup/login status=${r.status} ${await r.text()}`);
  return null;
}

(async () => {
  const tokens = [];
  const CHUNK = 50;
  for (let s = 1; s <= N; s += CHUNK) {
    const batch = [];
    for (let i = s; i < s + CHUNK && i <= N; i++) batch.push(one(i));
    const got = await Promise.all(batch);
    tokens.push(...got.filter(Boolean));
    process.stderr.write(`\r가입 진행 ${tokens.length}/${N}`);
  }
  process.stderr.write('\n');
  fs.writeFileSync(process.argv[3], JSON.stringify(tokens, null, 0));
  console.log(`토큰 ${tokens.length}개 저장`);
})();
