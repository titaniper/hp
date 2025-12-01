import { check, group, sleep } from 'k6';
import { config } from '../lib/config.js';
import { httpClient } from '../lib/httpClient.js';
import { couponIssueDuration, couponIssueErrors } from '../lib/metrics.js';
import { buildIdempotencyKey } from '../lib/utils.js';

const scenarioConfig = config.scenarios.couponIssue || {};

const templateId = Number(__ENV.COUPON_TEMPLATE_ID || config.defaults.couponIssueTemplateId || config.defaults.rushCouponTemplateId);
const userStart = Number(__ENV.COUPON_ISSUE_USER_START || config.defaults.couponIssueUserStart || config.defaults.rushUserStart || 1);
const userCount = Number(__ENV.COUPON_ISSUE_USER_COUNT || config.defaults.couponIssueUserCount || config.defaults.rushUserCount || 1);

function getUserId(vu, iteration) {
  if (!userCount || userCount <= 0) {
    return userStart;
  }
  const offset = (vu * 131 + iteration) % userCount;
  return userStart + offset;
}

export const options = {
  stages: scenarioConfig.stages || [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 150 },
    { duration: '30s', target: 0 }
  ],
  thresholds: config.thresholds,
  tags: {
    testType: 'coupon_issue',
    env: config.env
  },
  gracefulStop: scenarioConfig.gracefulStop || '30s'
};

export default function couponIssueScenario() {
  // const userId = getUserId(__VU, __ITER);
  const userId = 100;

  group('coupon-issue', () => {
    const response = httpClient.post(
      `/api/coupons/${templateId}/issue`,
      { userId: Number(userId) },
      {
        headers: {
          'Idempotency-Key': buildIdempotencyKey(`coupon-${userId}-${__ITER}`)
        }
      }
    );

    couponIssueDuration.add(response.timings.duration);

    const issued = check(response, {
      'coupon issued': (res) => res.status === 200 && res.json('userCouponId') !== undefined
    });

    if (!issued) {
      couponIssueErrors.add(1);
      // 디버깅을 위한 로그
      if (response.status !== 200) {
        console.log(`Request failed: status=${response.status}, body=${response.body}`);
      }
    }
  });

  // sleep(config.defaults.thinkTime);
}
