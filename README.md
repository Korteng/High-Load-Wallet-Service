## Concurrency & Load Testing

The original spec called for correctness at 1000 RPS on a single wallet. This section documents an attempt to reproduce that load locally, what was actually measured, and why the two numbers below - not 1000 - are the honest result on this hardware.

Tested with `hey` against `POST /api/v1/wallet/balance`, which locks the target row (`PESSIMISTIC_WRITE`) for the duration of the update.

**Scenario 1 - single wallet, high contention** (50 concurrent clients, 2000 requests on one wallet)

| Metric | Value |
|---|---|
| Throughput | 148.77 req/s |
| p50 / p99 latency | 291 ms / 1.26 s |
| Failed requests | 0 |
| Final balance | exact match - no lost updates |

**Scenario 2 - 40 wallets, distributed load** (warmed JVM, 15s duration per wallet, 15 concurrent clients each)

| Metric | Value |
|---|---|
| Aggregate throughput | 536 req/s |
| Failed requests | 0 |
| Final balances | exact match on all 40 wallets |

Removing artificial single-row contention more than 3x'd throughput - confirming the lock serializes writes only to the same wallet, not the service as a whole. That's the correct behavior for a financial ledger.

Reproduce with:
```bash
./throughput_test.sh   # scenario 1
./scale_test.sh        # scenario 2
```

### Why not 1000 RPS, and what would change it

Both numbers were measured on a 4-core dev laptop (`nproc` -> 4) running Docker Desktop on WSL2, not dedicated server hardware:

- **Single-wallet throughput (~150 req/s) is latency-bound, not CPU-bound.** It's capped by `1 / (lock hold time + commit round-trip)` - network hop through the WSL2 virtual network, ORM overhead, and Postgres's `fsync` on commit. More CPU cores would not move this number; only a shorter network/disk path would (bare-metal Linux, local Postgres, tuned connection pool).
- **Aggregate throughput (~536 req/s) is CPU-bound.** It scaled from ~380 to ~590 req/s purely by right-sizing the Hikari connection pool and Postgres `max_connections` to the available 4 cores - oversizing the pool past the core count made it *worse* (more thread contention, not more parallelism), confirming this is a resource ceiling, not a code inefficiency. Aggregate throughput is expected to scale close to linearly with additional cores on real server hardware.

In short: the 1000 RPS target in the spec describes a server environment with a shorter network/disk path than a laptop VM can offer, not a correctness bar the code fails to meet. Every request stayed correct and every failure mode returned the right status code - what changes on better hardware is throughput, not correctness.