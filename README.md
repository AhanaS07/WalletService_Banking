# wallet-service

A Spring Boot microservice that manages digital wallets (e.g. PAYTM, PHONEPE) for customers. It exposes a REST API to create wallets, top them up, pay bills, and transfer money between wallets, with balance and daily-spend business rules enforced on every operation.

The service is part of a larger microservice ecosystem: it registers with Eureka for service discovery, pulls configuration from Spring Cloud Config, persists data in MongoDB, and ships metrics/traces to Prometheus and Zipkin.

## Tech stack

- Java 21, Spring Boot 3.5.x
- Spring Web, Spring Data MongoDB, Bean Validation
- Spring Cloud: Netflix Eureka client, Config client
- Observability: Actuator, Micrometer + Prometheus, Micrometer Tracing + Zipkin (B3), Logstash Logback encoder
- API docs: springdoc OpenAPI / Swagger UI
- Shared DTO contracts via the `com.tnf:common-dto` library

## Endpoints

Base path: `/api/wallets`. All responses are wrapped in the shared `ApiResponse<T>` envelope; errors use the shared `ErrorResponse` contract.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/wallets` | Create a wallet for a customer (body: `CreateWalletRequest` — customerId, walletType, openingBalance). Returns `201 Created`. |
| `GET` | `/api/wallets` | Fetch all wallets. |
| `GET` | `/api/wallets/{walletId}` | Fetch a single wallet by id. |
| `GET` | `/api/wallets/customer/{customerId}` | Fetch all wallets belonging to a customer. |
| `POST` | `/api/wallets/{walletId}/add-money` | Top up a wallet (body: `AddMoneyRequest` — amount). |
| `POST` | `/api/wallets/{walletId}/pay-bill` | Debit a wallet to pay a bill (body: `PayBillRequest` — amount). |
| `POST` | `/api/wallets/{walletId}/transfer` | Transfer money to another wallet (body: `TransferRequest` — targetWalletId, amount). |

Swagger UI: `/api/wallets/swagger-ui.html` · OpenAPI JSON: `/api/wallets/v3/api-docs`

Operational endpoints (Actuator): `health`, `info`, `metrics`, `beans`, `httptrace`, `prometheus`.

## Business rules

Enforced in `WalletService`:

- **Max balance** — a wallet may not exceed **50,000**. Enforced on wallet creation, top-ups, and the credit side of transfers.
- **Daily spend limit** — debits (bill payments and the debit side of transfers) may not push a wallet's same-day spend over **20,000**. The daily counter rolls over automatically on the first debit of a new day.
- **Positive amounts** — top-up, debit, and transfer amounts must be positive; opening balances must be non-negative.
- **Insufficient balance** — a debit is rejected if the wallet balance is lower than the amount.
- **Self-transfer** — transferring to the same wallet is rejected.

### Transfers

MongoDB (standalone) has no multi-document transactions, so a transfer is done as a debit-then-credit with a **compensating rollback**: the source is debited and saved first, then the target is credited. If the credit fails, the debit is rolled back so no money moves. If the rollback itself fails, the balances are left inconsistent and a critical error is logged for manual reconciliation.

## Error handling

`GlobalExceptionHandler` maps exceptions to HTTP status codes:

| Exception | Status |
| --- | --- |
| `WalletNotFoundException` | `404 Not Found` |
| `InvalidAmountException`, `InsufficientBalanceException`, `WalletLimitExceededException` | `422 Unprocessable Entity` |
| `WalletTransferException` (debit rolled back — safe to retry) | `409 Conflict` |
| `WalletTransferException` (rollback failed — inconsistent state) | `500 Internal Server Error` |
| `IllegalArgumentException` (e.g. unknown wallet type), bean-validation failures | `400 Bad Request` |

## File structure

```
wallet-service/
├── pom.xml                         # Maven build & dependencies
├── mvnw, mvnw.cmd                  # Maven wrapper
├── src/main/java/com/tnf/wallet_service/
│   ├── WalletServiceApplication.java   # Spring Boot entry point
│   ├── controller/
│   │   └── WalletController.java       # REST endpoints under /api/wallets
│   ├── service/
│   │   └── WalletService.java          # Business logic & rules
│   ├── repositories/
│   │   └── WalletRepo.java             # Spring Data MongoDB repository
│   ├── entity/
│   │   ├── Wallet.java                 # Mongo document ("wallets" collection)
│   │   └── WalletType.java             # PAYTM | PHONEPE
│   └── exception/
│       ├── GlobalExceptionHandler.java # @RestControllerAdvice → HTTP responses
│       ├── WalletNotFoundException.java
│       ├── InsufficientBalanceException.java
│       ├── InvalidAmountException.java
│       ├── WalletLimitExceededException.java
│       └── WalletTransferException.java
├── src/main/resources/
│   ├── application.yml              # Base config (port 8084, MongoDB, Eureka, tracing)
│   ├── application-dev.yml          # Dev profile overrides
│   ├── application.properties
│   └── logback-spring.xml           # Logging config
└── src/test/java/com/tnf/wallet_service/
    └── WalletServiceApplicationTests.java
```

The `Wallet` entity is persisted to the MongoDB `wallets` collection and exposed to clients as a `WalletDTO` (with `walletType` serialized as a string).

## Running locally

The service depends on external infrastructure by default (see `application.yml`):

- MongoDB at `mongodb://localhost:27017/walletdb`
- Eureka server at `http://localhost:8761/eureka/`
- Zipkin at `http://localhost:9411/api/v2/spans`

Start it on port **8084** with:

```bash
./mvnw spring-boot:run
```

Build a runnable jar:

```bash
./mvnw clean package
```
