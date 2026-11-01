import http from 'k6/http';
import exec from 'k6/execution';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const tokens = new SharedArray('tokens', () => JSON.parse(open(__ENV.TOKENS)));
const CAP  = __ENV.CAP  || 'chaos-cap-01';
const BASE = __ENV.BASE || 'http://127.0.0.1:8080';

// 전원 동시 발사 - 최대 동시성으로 in-flight 트랜잭션을 늘린다
export const options = {
  scenarios: {
    burst: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || 400),
      iterations: 1,
      maxDuration: '60s',
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
  const token = tokens[(__VU - 1) % tokens.length];
  const res = http.post(`${BASE}/api/reservations`,
    JSON.stringify({ slotCapacityId: CAP }),
    { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` },
      timeout: '60s' });

  if (res.status === 201)      ok201.add(1);
  else if (res.status === 0)   connErr.add(1);
  else if (res.status === 409) { (res.body && res.body.indexOf('하루') >= 0) ? dup.add(1) : soldout.add(1); }
  else if (res.status >= 500)  err5xx.add(1);
  else                         other4xx.add(1);
}
