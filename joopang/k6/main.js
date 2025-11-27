import { fail } from 'k6';
import * as smoke from './scenarios/smoke.js';
import * as load from './scenarios/load.js';
import * as rush from './scenarios/rush.js';
import * as couponIssue from './scenarios/coupon-issue.js';
import * as purchaseStock from './scenarios/purchase-stock.js';

const scenarios = {
  smoke,
  load,
  rush,
  coupon_issue: couponIssue,
  purchase_stock: purchaseStock
};

const scenarioName = (__ENV.SCENARIO || 'smoke').toLowerCase().replace(/-/g, '_');
const scenario = scenarios[scenarioName];

if (!scenario) {
  fail(`지원하지 않는 SCENARIO 값입니다: ${scenarioName}`);
}

export const options = scenario.options;
export default scenario.default;
