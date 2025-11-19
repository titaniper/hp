import { SharedArray } from 'k6/data';

function parseCsv(text) {
  const [, ...rows] = text.trim().split('\n');
  return rows
    .map((row) => row.trim())
    .filter(Boolean)
    .map((row) => {
      const [email, token] = row.split(',');
      return { email: email?.trim(), token: token?.trim() };
    });
}

export const userTokens = new SharedArray('userTokens', () => {
  try {
    const csv = open('../data/users.csv');
    return parseCsv(csv);
  } catch (error) {
    const fallback = open('../data/users.sample.csv');
    return parseCsv(fallback);
  }
});
