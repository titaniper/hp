export function pickRandom(array) {
  if (!array || array.length === 0) {
    return null;
  }
  const index = Math.floor(Math.random() * array.length);
  return array[index];
}

export function buildIdempotencyKey(prefix = 'k6') {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}
