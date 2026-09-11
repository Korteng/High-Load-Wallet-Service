#!/usr/bin/env bash
mkdir -p /tmp/scale_test2
rm -rf /tmp/scale_test2/*
WALLET_COUNT=40
DURATION=20s
CONCURRENCY_PER_WALLET=15

declare -a IDS
for i in $(seq 1 $WALLET_COUNT); do
  id=$(curl -s -X POST http://localhost:8080/api/v1/wallet -d "initialBalance=0" | jq -r '.id')
  IDS+=("$id")
done
echo "Created $WALLET_COUNT wallets"

for id in "${IDS[@]}"; do
  payload=$(printf '{"id":"%s","operationType":"DEPOSIT","amount":1}' "$id")
  hey -z "$DURATION" -c "$CONCURRENCY_PER_WALLET" -m POST \
      -H "Content-Type: application/json" \
      -d "$payload" \
      http://localhost:8080/api/v1/wallet/balance > "/tmp/scale_test2/hey_$id.txt" &
done
wait

echo
echo "== Per-wallet throughput =="
grep -H "Requests/sec:" /tmp/scale_test2/hey_*.txt

TOTAL_RPS=$(grep -h "Requests/sec:" /tmp/scale_test2/hey_*.txt | awk '{sum+=$2} END{print sum}')
echo
echo "Aggregate RPS across $WALLET_COUNT wallets: $TOTAL_RPS"
