# Banking Transaction Processor

An in-memory service for managing accounts, moving money between them, and
reading back balances and ledgers.

Java 17, Spring Boot 3 (web), Maven, JUnit 5 and AssertJ.

```
./mvnw test          # run the tests
./mvnw spring-boot:run   # start the API on http://localhost:8080
```

## What it does

`BankingService` is the single entry point:

| Operation | Notes |
|---|---|
| `openAccount(id[, openingBalance])` | ids are unique and case-sensitive |
| `deposit(id, amount)` | |
| `withdraw(id, amount)` | cannot take the balance below zero |
| `transfer(from, to, amount)` | atomic, both ledgers updated or neither |
| `getBalance(id)` | |
| `getTransactionHistory(id)` | oldest first, immutable snapshot |

Every successful operation adds a `LedgerEntry` (type, amount, balance after,
timestamp, and for transfers the counterparty and a shared transfer id).
Failed operations leave no trace: no balance change and no ledger entry.

## HTTP API

| Method and path | Body | Success | Errors |
|---|---|---|---|
| `POST /accounts` | `{"accountId":"A1","openingBalance":100.00}` (balance optional) | 201 | 400, 409 |
| `GET /accounts/{id}/balance` | | 200 | 404 |
| `GET /accounts/{id}/transactions` | | 200, oldest first | 404 |
| `POST /accounts/{id}/deposits` | `{"amount":25.00}` | 201, the ledger entry | 400, 404 |
| `POST /accounts/{id}/withdrawals` | `{"amount":25.00}` | 201, the ledger entry | 400, 404, 422 |
| `POST /transfers` | `{"fromAccountId":"A","toAccountId":"B","amount":25.00}` | 201, the sender's ledger entry | 400, 404, 422 |

Errors come back as `{"error":"INSUFFICIENT_FUNDS","message":"..."}`:

| Status | When |
|---|---|
| 400 | bad amount (zero, negative, over 2 decimals, not a number), blank id, same-account transfer, missing or malformed body |
| 404 | unknown account |
| 409 | account id already exists |
| 422 | insufficient funds (the request is well formed but cannot be honoured) |

```
curl -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
     -d '{"accountId":"A","openingBalance":100}'
curl -X POST localhost:8080/accounts -H 'Content-Type: application/json' \
     -d '{"accountId":"B"}'
curl -X POST localhost:8080/transfers -H 'Content-Type: application/json' \
     -d '{"fromAccountId":"A","toAccountId":"B","amount":40.25}'
curl localhost:8080/accounts/B/transactions
```

## Architecture

```
AccountController   HTTP in and out only, no business rules
   |
BankingService   public API, validates requests and orchestrates
   |  ConcurrentHashMap<String, Account>
   v
Account          owns the balance and the ledger, guarded by one lock
   |
   v
Money, LedgerEntry, EntryType     immutable value types
```

- **Money is the validation gate.** A `Money` can only be constructed if it is
  non-negative, has at most two decimals and is under the cap. Code further in
  never has to ask "is this a valid amount?", so `Account` does no amount
  validation of its own.
- **Account owns its state.** Balance and ledger are only changed together,
  inside the account's lock. The funds check happens before any mutation, so a
  failed operation has nothing to undo.
- **AccountController** translates JSON to `Money` and back and nothing more.
  `ApiExceptionHandler` maps each domain exception to a status code. Responses
  use small DTO records rather than the domain types, so the JSON shape does not
  depend on how `Money` or `LedgerEntry` are built internally.
- **BankingService** checks the request (missing or zero amount, unknown
  account, same-account transfer), finds the accounts, and calls `credit` /
  `debit`. It is a plain class with no static state; whoever wires the
  application creates one instance and shares it. Tests create a fresh one each
  time, which is why it is not a singleton.
- The service creates no threads. It is written so that many threads can call
  it at once, and the concurrency test exercises exactly that.

## How a transfer stays correct

```
1. find(from), find(to)        both must exist before anything changes
2. lock both, in id order      avoids deadlock (see below)
3. from.debit(...)             the only step that can fail
4. to.credit(...)              cannot fail once the debit has passed
5. unlock both
```

- **Why lock both accounts?** Otherwise another thread could withdraw from the
  source between the balance check and the debit, or observe money that has left
  one account but not yet arrived in the other.
- **Why in id order?** If A to B and B to A each locked their own source first,
  each would hold one lock and wait for the other forever. A fixed global order
  means both go for the same lock first and one simply waits.
- **Why debit before credit?** Atomicity here comes from step ordering rather
  than rollback. The only step that can fail runs first; if it fails nothing has
  happened, and if it passes the rest cannot fail.
- The locks are `ReentrantLock`, because `debit` and `credit` lock the account
  again while the transfer already holds it. A non-reentrant lock would block
  on itself.
- Opening accounts uses `putIfAbsent` on the concurrent map, so two threads
  opening the same id cannot both succeed.

**Mapping to a database.** There is no database or framework here, so there is
no declarative transaction. With one, the locks and the debit-then-credit
ordering would be replaced by a single DB transaction: lock both rows
(`SELECT ... FOR UPDATE`, ordered by id for the same deadlock reason), update
both balances, insert both ledger rows, commit or roll back together.

## How it was approached

Built test-first, in this order, so each piece rests on tested ground:

1. `Money` and its edge cases.
2. Open an account and read the balance.
3. Deposit, then its ledger entry (with an injected `Clock` so timestamps can
   be asserted).
4. Withdraw, then overdraft, checking a rejected withdrawal leaves no ledger entry.
5. Transfer: happy path first, then unknown account, same account, and
   insufficient funds, each checked for "nothing changed".
6. Concurrency last: the parallel tests were written before the locking, to see
   them fail without it.

## Decisions worth knowing about

- **`Money` instead of raw `BigDecimal`.** Fixed at two decimals, never negative,
  capped at 999,999,999,999.99 per amount. `10.005` is rejected rather than
  rounded, because silently changing a customer's amount is worse than an error.
  `10`, `10.0` and `10.00` are equal.
- **Zero amounts are rejected** for deposit, withdraw and transfer. They are valid
  `Money` values (an account can have a zero balance) but not valid operations.
- **Transfers to the same account are rejected.** They would be a no-op that
  still pollutes both ledger sides.
- **Transfer atomicity.** Both accounts are looked up before anything changes,
  so an unknown destination can't leave the source debited. The debit runs first
  because it is the only step that can fail; the credit can't, so there is
  nothing to roll back.
- **Concurrency.** Each account has its own lock. A transfer takes both locks in
  account-id order, which prevents the A to B / B to A deadlock. Covered by a
  multi-threaded test.
- **Ledger and balance move together** under the account lock, so the last
  entry's `balanceAfter` always equals the current balance.
- **Opening balance is a ledger entry** (`OPENING_BALANCE`), so the ledger can
  always explain the balance. A zero opening balance writes nothing.
- **Clock is injected** so timestamps are testable. Ordering of history comes
  from append order, not from comparing timestamps, since two entries can share
  an instant.
- **Unknown or null account ids** raise `AccountNotFoundException`. A blank id
  when opening an account raises `InvalidAccountIdException`.
- **Deposit, withdraw and transfer return the ledger entry they wrote**, so the
  API can answer from the same atomic step instead of reading the balance again
  afterwards (when another request may have changed it).
- **Insufficient funds is a 422, not a 400.** The request is valid; the account
  state is what prevents it.
- **The service is registered as a bean in `BankingApplication`**, so the domain
  classes carry no Spring annotations and the unit tests need no framework.

## Limitations

- Everything is in memory; restarting loses all data.
- One currency only.
- No overdraft limits, fees, or daily limits.
- No idempotency keys. A client that retries a timed-out request will apply it twice.
- Account ids are used as given (no trimming or case folding).
- No authentication or authorisation on the endpoints.
- Ledgers grow without bound and history is not paginated.

## With more time

- Persistence behind an `AccountRepository` interface, with the account lock
  replaced by a database transaction.
- Idempotency key on each operation.
- Paged and date-filtered history.
- Reversals as new compensating entries (the ledger is append-only).

## Layout

```
src/main/java/bank
  BankingApplication   Spring Boot entry point, wires the service
  api/AccountController, ApiExceptionHandler, *Response   HTTP layer
  BankingService   public operations and the transfer locking
  Account          balance + ledger, guarded by one lock
  Money            validated amount value object
  LedgerEntry      one ledger line (record)
  EntryType        OPENING_BALANCE, DEPOSIT, WITHDRAWAL, TRANSFER_OUT, TRANSFER_IN
  *Exception       BankingException and its subtypes
src/test/java/bank
  MoneyTest, BankingServiceTest, BankingServiceConcurrencyTest, api/AccountControllerTest
```
