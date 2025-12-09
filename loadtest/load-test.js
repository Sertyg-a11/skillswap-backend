import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 5,           // 5 virtual users
    duration: '60s',  // run for 30 seconds
};

const BASE_URL = 'https://skillswap-frontend.fly.dev';

export default function () {
    // 1) GET /api/messages
    const resGet = http.get(`${BASE_URL}/api/messages?limit=50`);
    check(resGet, {
        'GET status is 200': (r) => r.status === 200,
    });

    // 2) POST /api/messages
    const payload = JSON.stringify({
        userId: '00000000-0000-0000-0000-000000000001',
        content: 'Load test message at ' + new Date().toISOString(),
    });

    const headers = { 'Content-Type': 'application/json' };

    const resPost = http.post(`${BASE_URL}/api/messages`, payload, { headers });
    check(resPost, {
        'POST status is 200/201': (r) => r.status === 200 || r.status === 201,
    });

    sleep(1); // small pause per "user"
}
