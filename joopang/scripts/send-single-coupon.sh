#!/usr/bin/env bash

set -euo pipefail

BASE_URL=${BASE_URL:-http://localhost:8080}
TEMPLATE_ID=${COUPON_TEMPLATE_ID:-700}
USER_ID=${COUPON_USER_ID:-100}
SECRET_KEY=${X_SECRET_KEY:-6BEQMfIRywavqv6tQGJ0H1nMDNW1mzTh}

payload=$(printf '{"userId":"%s"}' "$USER_ID")

curl \
  --silent \
  --show-error \
  --fail \
  --request POST \
  --header 'Content-Type: application/json;charset=UTF-8' \
  --header "X-Secret-Key: ${SECRET_KEY}" \
  --data "$payload" \
  "${BASE_URL}/api/coupons/${TEMPLATE_ID}/issue"

