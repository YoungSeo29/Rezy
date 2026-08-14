import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

const tokens = JSON.parse(open('./tokens.json'));

const created   = new Counter('created_201');
const conflict  = new Counter('conflict_409');
const connErr   = new Counter('conn_error_0');
const serverErr = new Counter('server_5xx');
const other     = new Counter('other');

export const options = { vus: 200, iterations: 200 };

export default function () {
    sleep(Math.random() * 0.2);        // ← 추가: 0~3초에 걸쳐 도착 분산

    const token = tokens[__VU - 1];
    const res = http.post(
        'http://localhost:8080/api/reservations',
        JSON.stringify({ slotCapacityId: '84ac6021-91eb-4f6a-abb0-aa1677e2cd84' }),
        { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } }
    );

    if (res.status === 201) created.add(1);
    else if (res.status === 409) conflict.add(1);
    else if (res.status === 0) connErr.add(1);
    else if (res.status >= 500) serverErr.add(1);
    else other.add(1);
}