#!/usr/bin/env bash
# Scenario 1: many concurrent deposits on ONE wallet (worst-case contention on a single row lock).
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080/api/v1/wallet}"
REQUESTS="${REQUESTS:-2000}"
CONCURRENCY="${CONCURRENCY:-50}"

id=$(curl -s -X POST "$BASE_URL" -d "initialBalance=0" | jq -r '.id')
echo "wallet: $id  ($REQUESTS requests, $CONCURRENCY concurrent clients)"
payload=$(printf '{"id":"%s","operationType":"DEPOSIT","amount":1}' "$id")

out=$(hey -n "$REQUESTS" -c "$CONCURRENCY" -m POST -H "Content-Type: application/json" -d "$payload" "$BASE_URL/balance")

echo "$out" | grep -E "Requests/sec:|(50|99)%+ in"
echo "$out" | grep -A3 "Status code distribution"

ok=$(echo "$out" | grep -E '^\s*\[200\]' | awk '{print $2}') || true
ok=${ok:-0}
balance=$(curl -s "$BASE_URL/wallets/$id" | jq -r '.newBalance')
echo "200 responses: $ok, final balance: $balance"
if [ "$(awk -v a="$ok" -v b="$balance" 'BEGIN{print (a==b)}')" = "1" ]; then
  echo "RESULT: balance equals the number of successful deposits (no lost updates)"
else
  echo "RESULT: MISMATCH (lost updates or unexpected balance)"; exit 1
fi
