# 17. Transactions (@Transactional)

**Concept:** Spring Data JPA. `TransferService` **debits** one account and **credits** another, and can **throw an exception between the two steps on purpose**. The same steps run under six different transaction setups, so the database state can be compared:
- with vs without `@Transactional`
- checked vs unchecked exceptions (optional extension)
- two common placement mistakes

Money is only moved, never created, so **the total of all balances must always stay 15000.00**. Any other total means a transfer was only half done.

## Tech

Java 17 · Maven · **Spring Boot 3.5.6** · Spring Data JPA / Hibernate 6.6 · **JpaTransactionManager** (auto-configured) · MySQL 8 / InnoDB

**Database:** shared Day 07 database `Day_007_Assigments`, table **`a17_accounts`**. It starts with two accounts:

| Account | Holder | Balance |
|---|---|---|
| #1 | Asha | 10000.00 |
| #2 | Ravi | 5000.00 |

The total is **15000.00**.

## Structure

```text
17-transactions/
├── pom.xml
└── src/main/
    ├── java/com/company/boot/
    │   ├── TransactionsApplication.java          @SpringBootApplication (run this)
    │   ├── entity/Account.java
    │   ├── repository/AccountRepository.java      JpaRepository (each method is itself transactional)
    │   ├── service/
    │   │   ├── TransferService.java               6 transfer methods, same steps, different @Transactional setup
    │   │   └── AccountService.java                balances + total, reset
    │   ├── controller/TransferController.java     POST /api/transfers, GET /api/accounts, POST /api/accounts/reset
    │   ├── dto/TransferRequest, TransferMode, AccountsView, ErrorResponse
    │   └── exception/
    │       ├── SimulatedFailureException.java     UNCHECKED (RuntimeException)
    │       ├── TransferException.java             CHECKED   (Exception)
    │       └── AccountNotFound..., InsufficientFunds..., GlobalExceptionHandler
    └── resources/
        ├── application.properties                 JpaTransactionManager + SQL logging
        ├── db-secret.properties                   password (git-ignored; copy db-secret.properties.example)
        └── schema.sql                             a17_accounts (InnoDB) + 2 rows
```

## The experiments: one `TransferService` method each

All of them call the same private `debitThenCredit()`:
1. load both accounts
2. **STEP 1 debit:** `saveAndFlush`, so the UPDATE is really executed in MySQL
3. *if `simulateFailure`*: **throw** an exception
4. **STEP 2 credit**

| `mode` | Method / annotation | Exception thrown |
|---|---|---|
| `WITH_TRANSACTION` | `@Transactional` | unchecked `SimulatedFailureException` |
| `WITHOUT_TRANSACTION` | *(none)* | unchecked |
| `CHECKED_DEFAULT` | `@Transactional` | **checked** `TransferException` |
| `CHECKED_ROLLBACK_FOR` | `@Transactional(rollbackFor = TransferException.class)` | **checked** |
| `SELF_INVOCATION` | *(none)*, calls `this.transferWithTransaction(...)` | unchecked |
| `SWALLOWED_EXCEPTION` | `@Transactional`, catches its own exception | unchecked, caught inside |

## How to run

1. Run `src/main/resources/schema.sql` in MySQL. It recreates `a17_accounts` with 10000 / 5000.
2. **Eclipse:** **File → Import → Maven → Existing Maven Projects**, select `17-transactions`, then right-click `TransactionsApplication` → **Run As → Java Application**.
3. **Before each experiment:** `POST http://localhost:8080/api/accounts/reset` sets the balances back to 10000 / 5000.
4. **Run a transfer** with `POST http://localhost:8080/api/transfers` (Postman, Body → raw → JSON):

   ```json
   {"fromAccountId":1,"toAccountId":2,"amount":1000,"mode":"WITH_TRANSACTION","simulateFailure":true}
   ```

5. **Check the result:**
   - `GET http://localhost:8080/api/accounts` shows the balances and `totalBalance`.
   - In MySQL: `SELECT * FROM Day_007_Assigments.a17_accounts;`
6. The Eclipse Console shows `Creating new transaction ...`, the `update` SQL, and then `Committing ...` or `Rolling back ...`.

## Observed output: with vs WITHOUT @Transactional (acceptance)

The same request was sent in both modes, after a reset: transfer 1000 from #1 to #2, failing after the debit.

### WITH `@Transactional` → **rolled back**

```text
-> 500  {"status":500,"error":"Internal Server Error","exceptionKind":"unchecked","message":"Simulated failure after debit, before credit"}

DEBUG JpaTransactionManager : Creating new transaction with name [com.company.boot.service.TransferService.transferWithTransaction]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
DEBUG SQL                   : update a17_accounts set balance=?,holder_name=? where account_id=?      <- debit EXECUTED in MySQL
INFO  TRANSFER              : STEP 1 debit  account 1 -1000 -> 9000.00
INFO  TRANSFER              : throwing UNCHECKED SimulatedFailureException before the credit
DEBUG JpaTransactionManager : Initiating transaction rollback
DEBUG JpaTransactionManager : Rolling back JPA transaction on EntityManager [SessionImpl(1749830495<open>)]

MySQL: #1=10000.00  #2=5000.00  total=15000.00        <- unchanged: the executed UPDATE was undone
```

### WITHOUT `@Transactional` → **debit committed, credit never happened**

```text
-> 500  {"status":500,"error":"Internal Server Error","exceptionKind":"unchecked","message":"Simulated failure after debit, before credit"}

DEBUG JpaTransactionManager : Creating new transaction with name [...SimpleJpaRepository.findById]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
DEBUG JpaTransactionManager : Committing JPA transaction ...
DEBUG JpaTransactionManager : Creating new transaction with name [...SimpleJpaRepository.findById]: ...readOnly
DEBUG JpaTransactionManager : Committing JPA transaction ...
DEBUG JpaTransactionManager : Creating new transaction with name [...SimpleJpaRepository.saveAndFlush]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
DEBUG SQL                   : update a17_accounts set balance=?,holder_name=? where account_id=?
DEBUG JpaTransactionManager : Initiating transaction commit
DEBUG JpaTransactionManager : Committing JPA transaction ...          <- the debit is now PERMANENT
INFO  TRANSFER              : STEP 1 debit  account 1 -1000 -> 9000.00
INFO  TRANSFER              : throwing UNCHECKED SimulatedFailureException before the credit
                                                                        (no rollback - there is no transaction to roll back)
MySQL: #1=9000.00  #2=5000.00  total=14000.00         <- 1000 has DISAPPEARED
```

Without a service transaction, **each repository call is its own tiny transaction** (Spring Data repository methods are `@Transactional`). It commits immediately, so the later exception cannot undo it.

**It gets worse with every failure.** Three failing transfers without a transaction, with no reset in between:

```text
MySQL: #1=9000.00  #2=5000.00  total=14000.00
MySQL: #1=8000.00  #2=5000.00  total=13000.00
MySQL: #1=7000.00  #2=5000.00  total=12000.00
```

### Both modes succeed when nothing fails

```text
WITH_TRANSACTION,    simulateFailure=false -> 200  {"accounts":[{"id":1,...,"balance":9000.00},{"id":2,...,"balance":6000.00}],"totalBalance":15000.00}
   Creating new transaction [TransferService.transferWithTransaction] -> update -> update -> Committing JPA transaction   (ONE transaction)
WITHOUT_TRANSACTION, simulateFailure=false -> 200  {"accounts":[{"id":1,...,"balance":9000.00},{"id":2,...,"balance":6000.00}],"totalBalance":15000.00}
   4 separate transactions: findById, findById, saveAndFlush (commit), saveAndFlush (commit)
```

The result is the same on the happy path, which is why the missing transaction goes unnoticed until something fails.

### Summary: database state after a failure between debit and credit

| Mode | Transaction log | #1 | #2 | **total** | Result |
|---|---|---|---|---|---|
| `WITH_TRANSACTION` | **Rolling back** | 10000.00 | 5000.00 | **15000.00** | ✅ atomic |
| `WITHOUT_TRANSACTION` | (per-call commits) | 9000.00 | 5000.00 | **14000.00** | ❌ money lost |

## Optional extension: checked vs unchecked exceptions

```text
CHECKED_DEFAULT       -> 500  {"exceptionKind":"checked","message":"Simulated checked failure after debit, before credit"}
  Creating new transaction with name [...TransferService.transferCheckedDefault]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
  update a17_accounts ...
  throwing CHECKED TransferException before the credit
  Initiating transaction COMMIT                         <- although an exception was thrown!
  Committing JPA transaction ...
  MySQL: #1=9000.00  #2=5000.00  total=14000.00         <- money lost

CHECKED_ROLLBACK_FOR  -> 500  {"exceptionKind":"checked","message":"Simulated checked failure after debit, before credit"}
  Creating new transaction with name [...TransferService.transferCheckedRollbackFor]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,-com.company.boot.exception.TransferException
  update a17_accounts ...
  throwing CHECKED TransferException before the credit
  Initiating transaction rollback
  Rolling back JPA transaction ...
  MySQL: #1=10000.00  #2=5000.00  total=15000.00        <- rolled back
```

| Exception type | Default `@Transactional` | With `rollbackFor = X.class` |
|---|---|---|
| unchecked (`RuntimeException`, `Error`) | **rollback** | rollback |
| checked (`Exception` subclasses) | **COMMIT** | **rollback** |

The `-com.company.boot.exception.TransferException` in the log is Spring's notation for "roll back on this exception".

## Failure / edge-case evidence: transaction placement pitfalls

**1. Self-invocation: `@Transactional` silently ignored.** `transferSelfInvocation()` has no annotation and calls `this.transferWithTransaction(...)`, which **does** have `@Transactional`:

```text
INFO  TRANSFER : self-invocation: calling this.transferWithTransaction(...) directly
DEBUG JpaTransactionManager : Creating new transaction with name [...SimpleJpaRepository.findById] ...     <- NOT TransferService.transferWithTransaction!
...
DEBUG JpaTransactionManager : Creating new transaction with name [...SimpleJpaRepository.saveAndFlush] ...
DEBUG SQL                   : update a17_accounts ...
DEBUG JpaTransactionManager : Committing JPA transaction ...
INFO  TRANSFER : throwing UNCHECKED SimulatedFailureException before the credit
MySQL: #1=9000.00  #2=5000.00  total=14000.00          <- same damage as having no annotation at all
```

`@Transactional` works through a **Spring proxy** that wraps the bean. A call from **inside** the same object (`this.method()`) does not pass through the proxy, so no transaction is started. The log proves it: no transaction named `transferWithTransaction` was created.

**2. Swallowed exception: the half-done work is committed, and the client is told it succeeded.**

```text
SWALLOWED_EXCEPTION -> 200  {"accounts":[{"id":1,...,"balance":9000.00},{"id":2,...,"balance":5000.00}],"totalBalance":14000.00}
  Creating new transaction with name [...TransferService.transferSwallowingException] ...
  update a17_accounts ...
  throwing UNCHECKED SimulatedFailureException before the credit
  WARN  TRANSFER : caught and ignored: Simulated failure after debit, before credit
  Initiating transaction commit
  Committing JPA transaction ...
  MySQL: #1=9000.00  #2=5000.00  total=14000.00
```

Spring decides between commit and rollback only from exceptions that **leave** the method. A `catch` inside the transactional method hides the failure. If an exception must be caught there, rethrow it or call `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`.

**3. Business failures inside a transaction roll back cleanly.**

| Request | Status | Message | Log | MySQL total |
|---|---|---|---|---|
| amount `999999` | **422** | `Insufficient funds in account 1: balance 10000.00, requested 999999` | `Creating new transaction` → `Rolling back` | 15000.00 |
| to account `99` | **404** | `Account not found with id 99` | `Creating new transaction` → `Rolling back` | 15000.00 |
| amount `-5` | **400** | `Amount must be greater than 0` | `Creating new transaction` → `Rolling back` | 15000.00 |

## Explanation

**Atomicity** is the "A" in ACID: a group of operations is **all or nothing**. A transfer is two UPDATEs (debit + credit). If only the first succeeds, money vanishes, as in `WITHOUT_TRANSACTION`, where the total fell from 15000 to 12000. A transaction makes the pair one indivisible unit: either both are committed, or neither is.

**Rollback** undoes **everything** done inside the transaction, including statements that were **already executed**. In `WITH_TRANSACTION`, the debit `update` really ran in MySQL (via `saveAndFlush`), but InnoDB kept it uncommitted. `Rolling back` discarded it, and nobody outside the transaction ever saw the 9000.

Spring's default rule:
- **roll back** on `RuntimeException` and `Error`
- **commit** on checked exceptions

Change it with `rollbackFor` / `noRollbackFor`. A common choice is `@Transactional(rollbackFor = Exception.class)`.

**Transaction placement:**
- **Put `@Transactional` on the service method** that performs the whole business operation (`transferWithTransaction`).
  - **Not on the repository:** each repository call is already transactional on its own, and that is exactly the problem.
  - **Not on the controller:** HTTP should not decide database boundaries.
  - **Not around more than needed:** long transactions hold locks.
- **It must be a `public` method called from another bean**, through the proxy. Calls via `this` bypass it (pitfall 1). On Spring 6, `protected`/package-private methods also work with class-based proxies, but `private` methods never do.
- **Let the exception leave the method,** or mark the transaction rollback-only (pitfall 2).
- **There is no class-level `@Transactional` here,** on purpose. It would also have wrapped the "without transaction" methods. In real code, a class-level `@Transactional(readOnly = true)` plus `@Transactional` on write methods is a common pattern (Assignment 16).
