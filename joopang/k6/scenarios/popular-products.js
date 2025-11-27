import { check, group, sleep } from 'k6';
import { config } from '../lib/config.js';
import { httpClient } from '../lib/httpClient.js';
import { popularProductsDuration, popularProductsErrors } from '../lib/metrics.js';

const scenarioConfig = config.scenarios.popularProducts || {};

const days = Number(__ENV.POPULAR_PRODUCTS_DAYS || config.defaults.popularProductsDays || 7);
const limit = Number(__ENV.POPULAR_PRODUCTS_LIMIT || config.defaults.popularProductsLimit || 20);
const cacheBypassPercent = Number(__ENV.POPULAR_PRODUCTS_CACHE_BYPASS_PERCENT || config.defaults.popularProductsCacheBypassPercent || 0);
const thinkTime = Number(__ENV.POPULAR_PRODUCTS_THINK_TIME || config.defaults.thinkTime || 1);

function shouldBypassCache() {
  if (!cacheBypassPercent || cacheBypassPercent <= 0) {
    return false;
  }
  return Math.random() * 100 < cacheBypassPercent;
}

function buildPopularProductsPath() {
  const basePath = `/api/products/top?days=${days}&limit=${limit}`;
  if (!shouldBypassCache()) {
    return basePath;
  }
  return `${basePath}&fresh=${Date.now()}-${__VU}-${__ITER}`;
}

export const options = {
  stages: scenarioConfig.stages || [
    { duration: '30s', target: 30 },
    { duration: '1m', target: 80 },
    { duration: '30s', target: 0 }
  ],
  thresholds: config.thresholds,
  tags: {
    testType: 'popular_products',
    env: config.env
  },
  gracefulStop: scenarioConfig.gracefulStop || '20s'
};

export default function popularProductsScenario() {
  group('popular-products', () => {
    const response = httpClient.get(buildPopularProductsPath());

    popularProductsDuration.add(response.timings.duration);

    const ok = check(response, {
      'popular products fetched': (res) => res.status === 200,
      'has products array': (res) => res.json('products') !== undefined
    });

    if (!ok) {
      popularProductsErrors.add(1);
      console.log(`Popular products request failed: status=${response.status}, body=${response.body}`);
    }
  });

  sleep(thinkTime);
}
