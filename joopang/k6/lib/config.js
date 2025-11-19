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
    ...(loaded?.defaults || {})
  },
  scenarios: loaded?.scenarios || {},
  thresholds: loaded?.thresholds || {}
};
