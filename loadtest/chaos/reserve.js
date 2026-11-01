import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const tokens = new SharedArray('tokens', () => JSON.parse(open(__ENV.TOKENS)));
const CAP  = __ENV.CAP  || 'chaos-cap-01';
const BASE = __ENV.BASE || 'http://127.0.0.1:8080';

export const options = {
  scenarios: {
    reserve: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '20s',
      preAllocatedVUs: Number(__ENV.VUS || 60),
      maxVUs: Number(__ENV.VUS || 60),
      gracefulStop: '30s',
    },
  },
};

const ok201    = new Counter('ok_201');
const soldout  = new Counter('soldout_409');
const dup      = new Counter('dup_409');
const other4xx = new Counter('other_4xx');
const err5xx   = new Counter('server_5xx');
const connErr  = new Counter('conn_error_0');

export default function () {
  // iterationInTest = 시나리오 전체에서 고유한 0-based 번호 → 토큰 1인 1회 보장
  const i = exec.scenario.iterationInTest;
  if (i >= tokens.length) return;
  const token = tokens[i];

  const res = http.post(`${BASE}/api/reservations`,
    JSON.stringify({ slotCapacityId: CAP }),
    { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      timeout: '30s' });

  if (res.status === 201)      ok201.add(1);
  else if (res.status === 0)   connErr.add(1);
  else if (res.status === 409) { (res.body && res.body.indexOf('하루') >= 0) ? dup.add(1) : soldout.add(1); }
  else if (res.status >= 500)  err5xx.add(1);
  else                         other4xx.add(1);
}
