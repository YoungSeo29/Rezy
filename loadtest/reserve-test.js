import http from 'k6/http';

const tokens = JSON.parse(open('./tokens.json'));  // 같은 폴더에 있으니 상대경로로 바로 읽힘

export const options = {
    vus: 200,
    iterations: 200,
};

export default function () {
    const token = tokens[__VU - 1];
    http.post(
        'http://localhost:8080/api/reservations',
        JSON.stringify({ slotCapacityId: '84ac6021-91eb-4f6a-abb0-aa1677e2cd84' }),
        { headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${token}` } }
    );
}