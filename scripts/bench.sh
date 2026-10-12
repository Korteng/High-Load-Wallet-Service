#!/usr/bin/env bash
# Throwaway load-test stack. Usage: scripts/bench.sh up | run | down
#   up   - build and start a separate stack (project "wallet-bench", API on 127.0.0.1:18080)
#   run  - warm-up, then single-wallet and multi-wallet load tests against that stack
#   down - remove the stack and its volume
set -euo pipefail

cd "$(dirname "$0")/.."
export BASE_URL="http://127.0.0.1:18080/api/v1/wallet"
stack() {
  docker compose -p wallet-bench --env-file bench/bench.env \
    -f docker-compose.yml -f bench/docker-compose.bench.yml "$@"
}

case "${1:-}" in
  up)
    stack up -d --build
    echo -n "waiting for the API"
    for _ in $(seq 1 60); do
      if [ "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL?initialBalance=0")" = "200" ]; then
        echo " ready"; exit 0
      fi
      echo -n "."; sleep 2
    done
    echo " not ready after 120 s" >&2; exit 1
    ;;
  run)
    echo "== warm-up (discarded) =="
    DURATION=10s WALLET_COUNT=10 ./scale_test.sh > /dev/null
    echo "== scenario 1: single wallet =="
    ./single_wallet_test.sh
    echo
    echo "== scenario 2: 40 wallets =="
    ./scale_test.sh
    ;;
  down)
    stack down -v
    ;;
  *)
    echo "usage: $0 up|run|down" >&2; exit 2
    ;;
esac
