import { check, group, sleep } from 'k6';
import { config } from '../lib/config.js';
import { httpClient } from '../lib/httpClient.js';
import { orderCreationDuration, orderErrors, stockCheckDuration, stockCheckErrors } from '../lib/metrics.js';
import { buildIdempotencyKey } from '../lib/utils.js';

const scenarioConfig = config.scenarios.purchaseStock || {};

const productId = Number(__ENV.PURCHASE_PRODUCT_ID || config.defaults.purchaseProductId || config.defaults.rushProductId);
const productItemId = Number(__ENV.PURCHASE_PRODUCT_ITEM_ID || config.defaults.purchaseProductItemId || config.defaults.rushProductItemId);
const purchaseQuantity = Number(__ENV.PURCHASE_QUANTITY || config.defaults.purchaseQuantity || 1);
const userStart = Number(__ENV.PURCHASE_USER_START || config.defaults.purchaseUserStart || config.defaults.rushUserStart || 1);
const userCount = Number(__ENV.PURCHASE_USER_COUNT || config.defaults.purchaseUserCount || config.defaults.rushUserCount || 1);

function getUserId(vu, iteration) {
  if (!userCount || userCount <= 0) {
    return userStart;
  }
  const offset = (vu * 97 + iteration) % userCount;
  return userStart + offset;
}

export const options = {
  stages: scenarioConfig.stages || [
    { duration: '1m', target: 30 },
    { duration: '3m', target: 80 },
    { duration: '1m', target: 0 }
  ],
  thresholds: config.thresholds,
  tags: {
    testType: 'purchase_stock',
    env: config.env
  },
  gracefulStop: scenarioConfig.gracefulStop || '45s'
};

export default function purchaseStockScenario() {
  const userId = getUserId(__VU, __ITER);

  const stockOk = group('stock-check', () => {
    const response = httpClient.get(`/api/products/${productId}/stock?quantity=${purchaseQuantity}`);
    stockCheckDuration.add(response.timings.duration);

    const ok = check(response, {
      'stock endpoint ok': (res) => res.status === 200 && res.json('available') !== undefined
    });

    if (!ok) {
      stockCheckErrors.add(1);
    }

    return ok;
  });

  if (!stockOk) {
    sleep(config.defaults.thinkTime);
    return;
  }

  const orderId = group('order-create', () => {
    const orderPayload = {
      userId,
      recipientName: `Stock Test User ${userId}`,
      couponId: null,
      memo: 'k6 stock test',
      imageUrl: null,
      zoneId: 'Asia/Seoul',
      items: [
        {
          productId,
          productItemId,
          quantity: purchaseQuantity
        }
      ]
    };

    const response = httpClient.post('/api/orders', orderPayload, {
      headers: {
        'Idempotency-Key': buildIdempotencyKey(`order-${userId}-${__ITER}`)
      }
    });

    orderCreationDuration.add(response.timings.duration);

    const created = check(response, {
      'order created': (res) => res.status === 200 && res.json('orderId') !== undefined
    });

    if (!created) {
      orderErrors.add(1);
      return null;
    }

    return response.json('orderId');
  });

  if (!orderId) {
    sleep(config.defaults.thinkTime);
    return;
  }

  const paymentResponse = httpClient.post(
    `/api/orders/${orderId}/payment`,
    { userId },
    {
      headers: {
        'Idempotency-Key': buildIdempotencyKey(`payment-${orderId}`)
      }
    }
  );

  const paid = check(paymentResponse, {
    'payment processed': (res) => res.status === 200 && res.json('status') === 'PAID'
  });

  if (!paid) {
    orderErrors.add(1);
  }

  sleep(config.defaults.thinkTime);
}
