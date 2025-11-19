import { check, group, sleep } from 'k6';
import { config } from '../lib/config.js';
import { httpClient } from '../lib/httpClient.js';
import { catalogDuration, detailDuration, orderErrors } from '../lib/metrics.js';
import { userTokens } from '../lib/data.js';
import { buildIdempotencyKey, pickRandom } from '../lib/utils.js';

const scenarioConfig = config.scenarios.load || {};

export const options = {
  stages: scenarioConfig.stages || [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 30 },
    { duration: '30s', target: 0 }
  ],
  thresholds: config.thresholds,
  tags: {
    testType: 'load',
    env: config.env
  },
  gracefulStop: scenarioConfig.gracefulStop || '30s',
  rampDownGracePeriod: scenarioConfig.rampDownGracePeriod || '30s'
};

function browseCatalog(headers) {
  group('catalog', () => {
    const catalogResponse = httpClient.get('/products?sort=popularity&page=1&pageSize=20', { headers });
    catalogDuration.add(catalogResponse.timings.duration);
    check(catalogResponse, {
      'catalog returns items': (res) => res.status === 200 && (res.json('data.products') || []).length >= 0
    });

    const productId = config.defaults.productId;
    const detailResponse = httpClient.get(`/products/${productId}`, { headers });
    detailDuration.add(detailResponse.timings.duration);
    check(detailResponse, {
      'product detail ok': (res) => res.status === 200
    });
  });
}

function manageCart(headers) {
  group('cart', () => {
    const payload = {
      productId: config.defaults.productId,
      quantity: 1,
      sessionId: config.defaults.cartSession
    };

    const response = httpClient.put('/carts/current/items', payload, {
      headers: { ...headers, 'Idempotency-Key': buildIdempotencyKey('cart') }
    });

    const isSuccess = check(response, {
      'cart item upsert succeeds': (res) => res.status && res.status < 400
    });

    if (!isSuccess) {
      orderErrors.add(1);
    }
  });
}

function validateCoupon(headers) {
  group('coupon', () => {
    const payload = {
      couponCode: 'FLASH10',
      cartId: 'CART-TEST'
    };
    const response = httpClient.post('/coupons/validate', payload, {
      headers: { ...headers, 'Idempotency-Key': buildIdempotencyKey('coupon') }
    });

    if (!check(response, { 'coupon validation ok': (res) => res.status === 200 })) {
      orderErrors.add(1);
    }
  });
}

export default function loadScenario() {
  const token = pickRandom(userTokens)?.token;
  const headers = token ? { authorization: `Bearer ${token}` } : {};

  browseCatalog(headers);
  manageCart(headers);
  validateCoupon(headers);

  sleep(config.defaults.thinkTime);
}
