#!/usr/bin/env bash
# Scenario 2: the same deposit load spread over many wallets (locks only serialize writes to the same wallet).
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080/api/v1/wallet}"
WALLET_COUNT="${WALLET_COUNT:-40}"
DURATION="${DURATION:-15s}"
CONCURRENCY_PER_WALLET="${CONCURRENCY_PER_WALLET:-15}"

OUT=$(mktemp -d)
declare -a IDS
for _ in $(seq 1 "$WALLET_COUNT"); do
  IDS+=("$(curl -s -X POST "$BASE_URL" -d "initialBalance=0" | jq -r '.id')")
done
echo "Created $WALLET_COUNT wallets, running $DURATION with $CONCURRENCY_PER_WALLET clients each"

for id in "${IDS[@]}"; do
  payload=$(printf '{"id":"%s","operationType":"DEPOSIT","amount":1}' "$id")
  hey -z "$DURATION" -c "$CONCURRENCY_PER_WALLET" -m POST \
      -H "Content-Type: application/json" -d "$payload" "$BASE_URL/balance" > "$OUT/hey_$id.txt" &
done
wait

TOTAL_RPS=$(grep -h "Requests/sec:" "$OUT"/hey_*.txt | awk '{sum+=$2} END{printf "%.2f", sum}')
echo "Aggregate RPS across $WALLET_COUNT wallets: $TOTAL_RPS"

echo "== Verification: balance must equal the number of 200 responses, no other responses, no client errors =="
FAILED=0
for id in "${IDS[@]}"; do
  f="$OUT/hey_$id.txt"
  # hey prints two blocks that look alike: "Status code distribution" ([200] 15 responses)
  # and "Error distribution" ([15] <message>); parse them separately
  ok=$(awk '/Status code distribution/{b=1;next} /^$/{b=0} b && $1=="[200]"{print $2}' "$f") || true
  ok=${ok:-0}
  other=$(awk '/Status code distribution/{b=1;next} /^$/{b=0} b && $1!="[200]"{n+=$2} END{print n+0}' "$f") || true
  errors=$(awk '/Error distribution/{b=1;next} /^$/{b=0} b{gsub(/[\[\]]/,"",$1); n+=$1} END{print n+0}' "$f") || true
  balance=$(curl -s "$BASE_URL/wallets/$id" | jq -r '.newBalance')
  if [ "$(awk -v a="$ok" -v b="$balance" 'BEGIN{print (a==b)}')" != "1" ] || [ "$other" -ne 0 ] || [ "$errors" -ne 0 ]; then
    echo "PROBLEM wallet $id: 200 responses=$ok, balance=$balance, other status=$other, client errors=$errors"
    awk '/Error distribution/{b=1;next} /^$/{b=0} b' "$f" | head -2
    FAILED=1
  fi
done
if [ "$FAILED" -eq 0 ]; then
  echo "RESULT: all $WALLET_COUNT wallets match, no non-200 responses, no client errors"
else
  echo "RESULT: FAILED (the aggregate RPS above is not valid)"; exit 1
fi
