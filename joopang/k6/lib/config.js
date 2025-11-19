import { fail } from 'k6';

const env = (__ENV.K6_ENV || 'local').toLowerCase();

function readConfig() {
  const path = `../config/${env}.json`;
  try {
    return JSON.parse(open(path));
  } catch (error) {
    fail(`k6 config 파일(${path})을 읽을 수 없습니다: ${error}`);
  }
}

const loaded = readConfig();

export const config = {
  env,
  ...loaded,
  defaults: {
    thinkTime: 1,
    productId: 'P001',
    cartSession: 'smoke-session',
    rushCouponTemplateId: 900,
    rushProductId: 1000,
    rushProductItemId: 1500,
    rushUserStart: 1000,
    rushUserCount: 2000,
    ...(loaded?.defaults || {})
  },
  scenarios: loaded?.scenarios || {},
  thresholds: loaded?.thresholds || {}
};
