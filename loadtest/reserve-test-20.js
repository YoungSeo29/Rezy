import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

const tokens = JSON.parse(open('./tokens.json'));

const SLOTS = [
    '84ac6021-91eb-4f6a-abb0-aa1677e2cd84',
    'ee8bb429-a46e-4315-b3d1-11e6074af5e6',
    'b5c1f438-d325-4fae-a9a2-34350a039e52',
    '3e5821e3-d73d-479b-95f9-f256e92bea3c',
    '01191ff0-1146-4673-b4ef-9dc48b41d65d',
    '311eedb0-4d64-405e-ad82-096b17596999',
    '63ebce13-7cb2-4ca1-9a43-1027505ca5a2',
    '1977cae0-ad2c-42b1-9a0a-968b7414fd57',
    'cda4a22b-8886-4729-a508-e045a17a9c07',
    'fcfe4403-31c8-41fc-8c91-1c758d29245a',
    '243339c4-f42f-49a8-b827-381c152b7ffe',
    '38244086-0062-4442-b5a6-6dfdd6c3fa6f',
    '76d5cb58-4c11-4d1d-bea2-383ef18e0350',
    'baf9983f-f24a-4e8b-bcd6-a791cf8f9872',
    'bf813405-ea59-4825-9c67-49a1078b2841',
    '70bc4f16-ce07-49be-95af-128ac010351a',
    '7728cba6-aad7-4dff-b182-822576ddf737',
    '9a0df157-f042-4c37-b7a2-eeae7f108823',
    'fbd17f5b-2eaa-4361-b3ff-04336c65ee28',
    'dee02d9e-4651-42fa-9984-339ed055b4bb',
];

const created   = new Counter('created_201');
const conflict  = new Counter('conflict_409');
const connErr   = new Counter('conn_error_0');
const serverErr = new Counter('server_5xx');
const other     = new Counter('other');

export const options = { vus: 200, iterations: 200 };

export default function () {
    sleep(Math.random() * 0.2);

    const token  = tokens[__VU - 1];
    const slotId = SLOTS[(__VU - 1) % SLOTS.length];   // 200명을 20개 슬롯에 10명씩 분배

    const res = http.post(
        'http://localhost:8080/api/reservations',
        JSON.stringify({ slotCapacityId: slotId }),
        { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } }
    );

    if (res.status === 201) created.add(1);
    else if (res.status === 409) conflict.add(1);
    else if (res.status === 0) connErr.add(1);
    else if (res.status >= 500) serverErr.add(1);
    else other.add(1);
}