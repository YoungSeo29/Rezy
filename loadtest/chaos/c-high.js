import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const tokens = new SharedArray('tokens', () => JSON.parse(open(__ENV.TOKENS)));
const STORE = 'seed-store-0001';
const DATE  = __ENV.DATE;
const BASE  = 'http://127.0.0.1:8080';
const DUR   = __ENV.DURATION || '120s';
// 평상시에는 커넥션이 남아돌고, Redis 가 죽는 순간에만 VU 전원이 매달려 풀(50)을 넘기도록
const THINK = Number(__ENV.THINK || 0.5);

export const options = {
  scenarios: {
    // Redis 를 타는 요청 - 커넥션 풀을 점유하는 쪽
    query: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 80),
      duration: DUR,
      exec: 'slotQuery',
      gracefulStop: '40s',
    },
    // Redis 를 안 타는 요청 - 대조군 (DB 만 사용)
    login: {
      executor: 'constant-arrival-rate',
      rate: 10, timeUnit: '1s', duration: DUR,
      preAllocatedVUs: 30, maxVUs: 50,
      exec: 'loginOnly',
      gracefulStop: '40s',
    },
  },
};

const q200=new Counter('query_200'), qErr=new Counter('query_err'), q5xx=new Counter('query_5xx');
const l200=new Counter('login_200'), lErr=new Counter('login_err'), l5xx=new Counter('login_5xx');

export function slotQuery() {
  const t = tokens[__VU % tokens.length];
  const r = http.get(`${BASE}/api/stores/${STORE}/slots?date=${DATE}`,
    { headers: { 'Authorization': `Bearer ${t}` }, timeout: '60s' });
  if (r.status === 200) q200.add(1);
  else if (r.status === 0) qErr.add(1);
  else if (r.status >= 500) q5xx.add(1);
  sleep(THINK);
}

export function loginOnly() {
  const r = http.post(`${BASE}/api/auth/login`,
    JSON.stringify({ email: 'chaos-0001@test.com', password: 'Passw0rd!23' }),
    { headers: { 'Content-Type': 'application/json' }, timeout: '60s' });
  if (r.status === 200) l200.add(1);
  else if (r.status === 0) lErr.add(1);
  else if (r.status >= 500) l5xx.add(1);
}
