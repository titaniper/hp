import http from 'k6/http';
import { check } from 'k6';
import { config } from './config.js';

const baseHeaders = config.headers || {};

function buildUrl(path) {
  if (path.startsWith('http')) {
    return path;
  }
  return `${config.baseUrl.replace(/\/$/, '')}${path}`;
}

function parseTimeout(timeoutStr) {
  if (!timeoutStr) return 60000; // 기본 60초
  if (typeof timeoutStr === 'number') return timeoutStr;
  
  const match = timeoutStr.toString().match(/^(\d+)(s|m|h)?$/);
  if (!match) return 60000;
  
  const value = parseInt(match[1], 10);
  const unit = match[2] || 's';
  
  switch (unit) {
    case 's': return value * 1000;
    case 'm': return value * 60 * 1000;
    case 'h': return value * 60 * 60 * 1000;
    default: return value * 1000;
  }
}

function request(method, path, { body = null, headers = {}, params = {} } = {}) {
  // baseHeaders를 먼저 적용하고, 전달받은 headers로 덮어씀
  const mergedHeaders = { ...baseHeaders, ...headers };
  
  // k6의 http.request는 body를 세 번째 인자로, params를 네 번째 인자로 받음
  // timeout은 문자열('30s', '60s') 또는 숫자(밀리초) 형식
  const requestParams = {
    tags: { endpoint: path },
    timeout: config.timeout || '60s', // config에서 받은 문자열 그대로 사용
    headers: mergedHeaders,
    ...params
  };
  
  // body가 null이면 undefined로 변경 (k6는 null을 받지 않음)
  const requestBody = body !== null ? body : undefined;
  
  const response = http.request(method, buildUrl(path), requestBody, requestParams);
  return response;
}

function get(path, options) {
  return request('GET', path, options);
}

function post(path, body, options = {}) {
  return request('POST', path, { 
    ...options, 
    body: JSON.stringify(body), 
    headers: { 
      'Content-Type': 'application/json', 
      ...(options.headers || {}) 
    } 
  });
}

function put(path, body, options = {}) {
  return request('PUT', path, { ...options, body: JSON.stringify(body), headers: { 'Content-Type': 'application/json', ...(options.headers || {}) } });
}

function del(path, options) {
  return request('DELETE', path, options);
}

function assertResponse(response, description = 'request succeeded') {
  return check(response, {
    [description]: (res) => res.status && res.status < 400
  });
}

export const httpClient = {
  request,
  get,
  post,
  put,
  del,
  assertResponse
};
