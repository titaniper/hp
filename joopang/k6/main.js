import { fail } from 'k6';
import * as smoke from './scenarios/smoke.js';
import * as load from './scenarios/load.js';
import * as rush from './scenarios/rush.js';

const scenarios = {
  smoke,
  load,
  rush
};

const scenarioName = (__ENV.SCENARIO || 'smoke').toLowerCase();
const scenario = scenarios[scenarioName];

if (!scenario) {
  fail(`지원하지 않는 SCENARIO 값입니다: ${scenarioName}`);
}

export const options = scenario.options;
export default scenario.default;
