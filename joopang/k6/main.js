import { fail } from 'k6';
import * as smoke from './scenarios/smoke.js';
import * as load from './scenarios/load.js';

const scenarios = {
  smoke,
  load
};

const scenarioName = (__ENV.SCENARIO || 'smoke').toLowerCase();
const scenario = scenarios[scenarioName];

if (!scenario) {
  fail(`지원하지 않는 SCENARIO 값입니다: ${scenarioName}`);
}

export const options = scenario.options;
export default scenario.default;
