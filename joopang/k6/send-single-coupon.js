import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TEMPLATE_ID = __ENV.COUPON_TEMPLATE_ID || __ENV.TEMPLATE_ID || '700';
const USER_ID = __ENV.COUPON_USER_ID || __ENV.USER_ID || '100';
const SECRET_KEY = __ENV.X_SECRET_KEY || '6BEQMfIRywavqv6tQGJ0H1nMDNW1mzTh';

const ITERATIONS = Number(__ENV.SINGLE_COUPON_ITERATIONS || __ENV.ITERATIONS || 1);
const VUS = Number(__ENV.SINGLE_COUPON_VUS || __ENV.VUS || 1);

export const options = {
  vus: VUS,
  iterations: ITERATIONS,
  thresholds: {
    http_req_failed: ['rate<0.01']
  },
  tags: {
    testType: 'send_single_coupon'
  }
};

const issueDuration = new Trend('send_single_coupon_duration');

export default function sendSingleCoupon() {
  const response = http.post(
    `${BASE_URL}/api/coupons/${TEMPLATE_ID}/issue`,
    JSON.stringify({ userId: USER_ID }),
    {
      headers: {
        'Content-Type': 'application/json;charset=UTF-8',
        'X-Secret-Key': SECRET_KEY
      }
    }
  );

  issueDuration.add(response.timings.duration);

  const issued = check(response, {
    'coupon issued': (res) => res.status === 200,
    'user coupon id present': (res) => res.json('userCouponId') !== undefined
  });

  if (!issued) {
    console.error(`Coupon issue failed (status=${response.status}): ${response.body}`);
  }
}
