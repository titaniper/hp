import { check, sleep } from 'k6';
import { config } from '../lib/config.js';
import { httpClient } from '../lib/httpClient.js';
import { catalogDuration, detailDuration } from '../lib/metrics.js';

const scenarioConfig = config.scenarios.smoke || {};

export const options = {
  vus: scenarioConfig.vus || 1,
  duration: scenarioConfig.duration || '30s',
  thresholds: config.thresholds,
  tags: {
    testType: 'smoke',
    env: config.env
  }
};

export default function smokeScenario() {
  const catalogResponse = httpClient.get('/products?sort=popularity&page=1&pageSize=10');
  catalogDuration.add(catalogResponse.timings.duration);
  check(catalogResponse, {
    'catalog list succeeds': (res) => res.status === 200 && res.json('data.products') !== undefined
  });

  const productId = config.defaults.productId;
  const detailResponse = httpClient.get(`/products/${productId}`);
  detailDuration.add(detailResponse.timings.duration);
  check(detailResponse, {
    'product detail succeeds': (res) => res.status === 200
  });

  const topResponse = httpClient.get('/products/top?market=KR');
  check(topResponse, {
    'top products succeeds': (res) => res.status === 200
  });

  sleep(config.defaults.thinkTime);
}
