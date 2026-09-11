#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://localhost:8080/api/v1/wallet"
WALLET_COUNT=10
REQUESTS_PER_WALLET=200
CONCURRENCY_PER_WALLET=5
AMOUNT=10
INITIAL_BALANCE=1000

RESULTS_DIR=$(mktemp -d)
echo "Results dir: $RESULTS_DIR"

declare -a WALLET_IDS

echo "== Creating $WALLET_COUNT wallets =="
for i in $(seq 1 "$WALLET_COUNT"); do
  id=$(curl -s -X POST "$BASE_URL" -d "initialBalance=$INITIAL_BALANCE" | jq -r '.id')
  WALLET_IDS+=("$id")
  echo "wallet $i: $id"
done

echo
echo "== Running parallel load: $WALLET_COUNT wallets x $REQUESTS_PER_WALLET req (c=$CONCURRENCY_PER_WALLET each) =="
START=$(date +%s.%N)

for i in "${!WALLET_IDS[@]}"; do
  id="${WALLET_IDS[$i]}"
  payload=$(printf '{"id":"%s","operationType":"DEPOSIT","amount":%s}' "$id" "$AMOUNT")
  hey -n "$REQUESTS_PER_WALLET" -c "$CONCURRENCY_PER_WALLET" -m POST \
      -H "Content-Type: application/json" \
      -d "$payload" \
      "$BASE_URL/balance" > "$RESULTS_DIR/hey_$i.txt" &
done

wait
END=$(date +%s.%N)
WALL=$(echo "$END - $START" | bc)

TOTAL_REQUESTS=$((WALLET_COUNT * REQUESTS_PER_WALLET))
RPS=$(echo "$TOTAL_REQUESTS / $WALL" | bc -l)

echo
echo "== Aggregate throughput =="
printf "Total requests: %d\nWall time: %.2f sec\nAggregate RPS: %.2f\n" "$TOTAL_REQUESTS" "$WALL" "$RPS"

echo
echo "== Non-200 responses per wallet (should be none) =="
for i in "${!WALLET_IDS[@]}"; do
  grep -A3 "Status code distribution" "$RESULTS_DIR/hey_$i.txt" | tail -n +2
done

echo
echo "== Balance verification =="
FAILED=0
for i in "${!WALLET_IDS[@]}"; do
  id="${WALLET_IDS[$i]}"
  expected=$(echo "$INITIAL_BALANCE + $REQUESTS_PER_WALLET * $AMOUNT" | bc)
  actual=$(curl -s "$BASE_URL/wallets/$id" | jq -r '.newBalance')
  if [ "$(echo "$actual == $expected" | bc)" -eq 1 ]; then
    status="OK"
  else
    status="MISMATCH (expected $expected)"
    FAILED=1
  fi
  echo "wallet $i ($id): balance=$actual $status"
done

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All $WALLET_COUNT wallets match expected balance — no lost updates under concurrent multi-wallet load."
else
  echo "!!! Some wallets mismatched — investigate concurrency bug."
fi

echo
echo "Per-wallet hey summaries saved in: $RESULTS_DIR"
