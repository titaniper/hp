import { Counter, Trend } from 'k6/metrics';

export const catalogDuration = new Trend('catalog_duration', true);
export const detailDuration = new Trend('product_detail_duration', true);
export const couponIssueDuration = new Trend('coupon_issue_duration', true);
export const orderCreationDuration = new Trend('order_creation_duration', true);
export const orderErrors = new Counter('order_errors');
