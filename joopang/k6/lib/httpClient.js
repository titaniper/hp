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

function request(method, path, { body = null, headers = {}, params = {} } = {}) {
  const mergedHeaders = { ...baseHeaders, ...headers };
  const response = http.request(method, buildUrl(path), body, {
    tags: { endpoint: path },
    ...params,
    headers: mergedHeaders
  });
  return response;
}

function get(path, options) {
  return request('GET', path, options);
}

function post(path, body, options = {}) {
  return request('POST', path, { ...options, body: JSON.stringify(body), headers: { 'Content-Type': 'application/json', ...(options.headers || {}) } });
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
