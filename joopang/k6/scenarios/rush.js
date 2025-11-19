import { check, group } from 'k6';
import { config } from '../lib/config.js';
import { httpClient } from '../lib/httpClient.js';
import { couponIssueDuration, orderCreationDuration, orderErrors } from '../lib/metrics.js';
import { rushUsers } from '../lib/data.js';
import { buildIdempotencyKey } from '../lib/utils.js';

const scenarioConfig = config.scenarios.rush || {};
const vus = Number(__ENV.RUSH_VUS || scenarioConfig.vus || 2000);
const maxDuration = __ENV.RUSH_MAX_DURATION || scenarioConfig.maxDuration || '5m';

export const options = {
  scenarios: {
    rush: {
      executor: 'per-vu-iterations',
      vus,
      iterations: 1,
      maxDuration
    }
  },
  thresholds: config.thresholds,
  tags: {
    testType: 'rush',
    env: config.env
  }
};

function getUserForVu(vu) {
  const index = (vu - 1) % rushUsers.length;
  return rushUsers[index];
}

export default function rushScenario() {
  const user = getUserForVu(__VU);

  group('rush-flow', () => {
    const couponResponse = httpClient.post(
      `/api/coupons/${config.defaults.rushCouponTemplateId}/issue`,
      { userId: user.id },
      {
        headers: { 'Idempotency-Key': buildIdempotencyKey(`coupon-${user.id}`) }
      }
    );
    couponIssueDuration.add(couponResponse.timings.duration);

    const couponOk = check(couponResponse, {
      'coupon issued': (res) => res.status === 200 && res.json('userCouponId') !== undefined
    });

    if (!couponOk) {
      orderErrors.add(1);
      return;
    }

    const couponId = couponResponse.json('userCouponId');
    const orderPayload = {
      userId: user.id,
      recipientName: `Rush User ${user.id}`,
      couponId,
      memo: 'k6 rush test',
      imageUrl: null,
      zoneId: 'Asia/Seoul',
      items: [
        {
          productId: config.defaults.rushProductId,
          productItemId: config.defaults.rushProductItemId,
          quantity: 1
        }
      ]
    };

    const orderResponse = httpClient.post('/api/orders', orderPayload, {
      headers: { 'Idempotency-Key': buildIdempotencyKey(`order-${user.id}`) }
    });
    orderCreationDuration.add(orderResponse.timings.duration);

    if (!check(orderResponse, { 'order created': (res) => res.status === 200 })) {
      orderErrors.add(1);
    }
  });
}
