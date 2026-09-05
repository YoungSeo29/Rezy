import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { SharedArray } from 'k6/data';

const tokens = new SharedArray('tokens', () => JSON.parse(open('./tokens.json')));


const SLOTS = [
    'seed-cap-000000859',
    'seed-cap-000000862',
    'seed-cap-000000865',
    'seed-cap-000000868',
    'seed-cap-000000871',
    'seed-cap-000000874',
    'seed-cap-000000877',
    'seed-cap-000000880',
    'seed-cap-000000883',
    'seed-cap-000000886',
];

const created   = new Counter('created_201');
const conflict  = new Counter('conflict_409');
const connErr   = new Counter('conn_error_0');
const serverErr = new Counter('server_5xx');
const other     = new Counter('other');

const VUS = 500;
const ITER_PER_VU = 6;      // 500 × 6 = 3000 (토큰 수와 동일)

export const options = {
    scenarios: {
        heavy: {
            executor: 'per-vu-iterations',   // VU당 정확히 N회 보장
            vus: VUS,
            iterations: ITER_PER_VU,
            maxDuration: '2m',
        },
    },
};

export default function () {
    // VU마다 겹치지 않는 고유 인덱스 (0 ~ 2999)
    const idx = (__VU - 1) * ITER_PER_VU + __ITER;

    const token = tokens[idx];
    const slot  = SLOTS[idx % SLOTS.length];

    const res = http.post(
        'http://localhost:8080/api/reservations',
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
        'http://localhost:8080/api/reservations',
        JSON.stringify({ slotCapacityId: SLOTS[0] }),
        { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${tokens[0]}` } }
    );
    console.log(`[사전점검] status=${res.status} body=${res.body}`);
    if (res.status !== 201) throw new Error('사전점검 실패 - 토큰/슬롯 확인 필요');
}