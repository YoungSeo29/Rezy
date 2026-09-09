import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';
const BASE = __ENV.BASE_URL || 'http://localhost:8080';

const SLOTS = [
    'seed-cap-01-2',
];

const tokens = new SharedArray('tokens', () => JSON.parse(open('./tokens.json')));

const created   = new Counter('created_201');
const conflict  = new Counter('conflict_409');
const connErr   = new Counter('conn_error_0');
const serverErr = new Counter('server_5xx');
const other     = new Counter('other');

const VUS = 500;
const ITER_PER_VU = 6;      // 500 × 6 = 3000 (분산형과 동일한 총 요청 수)

export const options = {
    scenarios: {
        focus: {
            executor: 'per-vu-iterations',   // VU당 정확히 N회 보장
            vus: VUS,
            iterations: ITER_PER_VU,
            maxDuration: '5m',               // 비관적 락은 직렬화 때문에 1분 넘게 걸릴 수 있음
        },
    },
};

export default function () {
    // VU마다 겹치지 않는 고유 인덱스 (0 ~ 2999)
    const idx = (__VU - 1) * ITER_PER_VU + __ITER;

    const token = tokens[idx];
    const slot  = SLOTS[0];

    const res = http.post(
        `${BASE}/api/reservations`,
        JSON.stringify({ slotCapacityId: slot }),
        { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } }
    );

    if (res.status === 201) created.add(1);
    else if (res.status === 409) conflict.add(1);
    else if (res.status === 0) connErr.add(1);
    else if (res.status >= 500) serverErr.add(1);
    else other.add(1);
}

export function setup() {
    const res = http.post(
        `${BASE}/api/reservations`,
        JSON.stringify({ slotCapacityId: SLOTS[0] }),
        { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokens[0]}` } }
    );
    console.log(`[사전점검] status=${res.status} body=${res.body}`);
    if (res.status !== 201) throw new Error('사전점검 실패 - 토큰/슬롯 확인 필요');
}