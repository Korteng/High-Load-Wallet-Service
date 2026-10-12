# High-Load Wallet Service

A REST service for managing wallet balances (`DEPOSIT` / `WITHDRAW`) built to stay correct under concurrent load on the same wallet - no lost updates, no 5xx on invalid input.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED)

## Overview

Financial-style balance updates are a classic race-condition trap: two concurrent requests read the same balance, both compute their own new value, and one write silently overwrites the other. This service prevents that with row-level pessimistic locking (`SELECT ... FOR UPDATE`) on every balance mutation, verified under actual concurrent load rather than assumed correct.

## Features

- Create wallets, deposit and withdraw with pessimistic row locking (no lost updates).
- **Operation history (ledger):** every balance change is stored in `wallet_operation` with type, amount, resulting balance, category, description and an optional idempotency key.
- **Idempotent operations:** a repeated request with the same idempotency key returns the original result and does not change the balance again (unique constraint + check under the wallet lock).
- **Telegram bot** for personal expense tracking on top of the same service (see below). Optional, disabled by default.

## Tech Stack

- **Java 17**, **Spring Boot 3.5** (Web, Validation, Data JPA)
- **PostgreSQL 15**, schema migrations via **Liquibase**
- **Docker / Docker Compose** - fully containerized, config via env vars, no rebuild needed to change settings
- **JUnit 5 + Mockito** for controller/validation tests, **Testcontainers** for repository integration tests
- **[hey](https://github.com/rakyll/hey)** for load testing

## Architecture

```mermaid
flowchart LR
    Client([Client]) -->|POST /wallet<br/>POST /wallet/balance<br/>GET /wallet/wallets/id| Controller[WalletController]
    Controller --> Validation{"Bean Validation<br/>(@NotNull, @Positive)"}
    Validation -- invalid --> Handler[GlobalExceptionHandler]
    Validation -- valid --> Service[WalletService]
    Service -->|SELECT ... FOR UPDATE| Repo[(WalletRepository)]
    Repo --> DB[(PostgreSQL)]
    Service -- InsufficientFundsException<br/>WalletNotFoundException --> Handler
    Handler -->|400 / 404 JSON error| Client
    Service --> Client
```

## API

**Create a wallet**
```
POST /api/v1/wallet?initialBalance=1000
```
```json
{ "id": "0bbe090c-6b9c-4eab-86ac-d42f205fbfbb", "newBalance": 1000 }
```

**Deposit / Withdraw**
```
POST /api/v1/wallet/balance
{
  "id": "0bbe090c-6b9c-4eab-86ac-d42f205fbfbb",
  "operationType": "DEPOSIT",
  "amount": 10000.00
}
```
```json
{ "id": "0bbe090c-6b9c-4eab-86ac-d42f205fbfbb", "newBalance": 11000.00 }
```

**Get balance**
```
GET /api/v1/wallet/wallets/0bbe090c-6b9c-4eab-86ac-d42f205fbfbb
```
```json
{ "id": "0bbe090c-6b9c-4eab-86ac-d42f205fbfbb", "newBalance": 11000.00 }
```

**Operation history**
```
GET /api/v1/wallet/wallets/0bbe090c-6b9c-4eab-86ac-d42f205fbfbb/operations?limit=20
```
Newest first, `limit` is clamped to 1..100.

**Insufficient funds**
```
POST /api/v1/wallet/balance
{ "id": "0bbe090c-6b9c-4eab-86ac-d42f205fbfbb", "operationType": "WITHDRAW", "amount": 30000 }
```
```json
{ "message": "Недостаточно средств на Вашем счёте." }
```

![Создание кошелька](docs/screenshots/postman-1-create-wallet.jpeg)
![Пополнение баланса](docs/screenshots/postman-2-deposit.jpeg)
![Проверка баланса](docs/screenshots/postman-3-get-balance.jpeg)
![Успешное снятие средств](docs/screenshots/postman-4-withdraw-success.jpeg)
![Ошибка при недостатке средств](docs/screenshots/postman-5-withdraw-insufficient-funds.jpeg)

## Error Handling

| Condition | Status | Body |
|---|---|---|
| Malformed / unreadable JSON | 400 | `{"message": "Невалидный JSON"}` |
| Validation failure (`@NotNull`, `@Positive`) | 400 | `{"message": "<field>: <reason>"}` |
| Insufficient funds on withdrawal | 400 | `{"message": "Недостаточно средств на Вашем счёте."}` |
| Wallet not found | 404 | `{"message": "Кошелёк с ID <id> не найден"}` |
| Unexpected error | 500 | `{"message": "Внутренняя ошибка сервера"}` |

No request produces an unhandled 5xx - every failure mode above is explicitly caught in `GlobalExceptionHandler` and covered by `WalletControllerValidationTest`.

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

> **Note:** both measurements were taken before the operation history was added. Every balance change now performs one extra `INSERT` into `wallet_operation` inside the same transaction, so current numbers will be somewhat lower; the benchmark has not been re-run yet.

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

## Running Locally

```bash
docker compose up --build -d
```

The image is built inside Docker (multi-stage, Maven in the build stage), so only Docker is required.

App: `http://localhost:8080` · DB: `localhost:5432` (`wallet_db` / `wallet_user`)

Config is fully env-driven (see `docker-compose.yml`) - no rebuild needed to change datasource, credentials, or Liquibase behavior.

## Telegram Bot (optional)

A personal expense tracker built on the wallet service. Long polling through the Telegram Bot API with Spring `RestClient`, no third-party Telegram library.

1. Create a bot with [@BotFather](https://t.me/BotFather) and copy the token.
2. `cp .env.example .env` and set `TELEGRAM_ENABLED=true`, `TELEGRAM_TOKEN`, `TELEGRAM_ALLOWED_CHAT_IDS` (comma-separated chat ids; **leave it empty only if you want anyone to be able to use the bot**). `.env` is git-ignored.
3. `docker compose up --build -d`

| Message | Result |
|---|---|
| `/start` | Links the chat to a new wallet |
| `кофе 250`, `250 такси`, `бар 700 руб` | Expense, category assigned by keywords |
| `+5000 зарплата` | Income |
| `аптека 1 200,50` | Thousands separated by a space, decimal comma |
| `/balance`, `/history` | Balance, last 10 operations |

Each Telegram update is processed with the idempotency key `tg-<chatId>-<updateId>`, so a redelivered update cannot be applied twice. Categories are assigned by a keyword classifier behind the `CategoryClassifier` interface.

**Limitations:** `кофе 5 250` is read as 5250 (a space between digit groups is treated as a thousands separator); the polling offset is kept in memory only; the bot is a single-instance personal tool, not a multi-tenant service.

## LLM Categorization (optional)

Expenses the keyword classifier cannot place (it returns «прочее») can be categorized by a language model: [Spring AI](https://spring.io/projects/spring-ai) 1.1 with an OpenAI-compatible client pointed at the GigaChat API. Disabled by default.

```
LLM_ENABLED=true
GIGACHAT_AUTH_KEY=<authorization key from the GigaChat API cabinet>
LLM_MODEL=GigaChat-2          # optional
```

How it behaves:

- **Keywords first.** The model is called only for unrecognized expenses, never for income or words the keyword list already knows, so most messages cost nothing and never leave the machine.
- **Structured output.** The model must answer `{"category": "..."}`; the answer is accepted only if it is one of the known categories (`еда`, `транспорт`, `жильё`, `здоровье`, `развлечения`, `прочее`). A wrong, malformed or injected answer cannot create a new category.
- **Failure-tolerant.** Timeouts, API errors or an invalid answer fall back to the keyword result; the bot keeps working without the model.
- **Token handling.** GigaChat access tokens live 30 minutes and are obtained from the authorization key via OAuth; `GigaChatTokenProvider` refreshes the token before it expires. Neither the key nor the token is logged.
- **Certificates.** The GigaChat endpoints use the Russian Trusted CA; the public root and sub-CA certificates are in `certs/` and imported into the JVM truststore in the Docker image.
- **Data sent to the provider:** only the operation description, with phone/card-like digit sequences and emails masked and the text cut to 100 characters. Amounts, wallet ids and chat data are not sent. Names and addresses written inside a description are **not** detected, so the description is still personal data sent to a third party (check 152-FZ before using real user data).
- Results are cached in memory (500 entries) to save tokens.

## Testing

```bash
mvn test
```

Docker must be running: repository tests and the application context test run against a real PostgreSQL 15 container (Testcontainers) with the actual Liquibase changelog, so the migrations themselves are verified.

Covers: validation edge cases (`WalletControllerValidationTest`), wallet service unit tests (including idempotency and history recording), message parser and bot command handler tests, repository integration tests.
