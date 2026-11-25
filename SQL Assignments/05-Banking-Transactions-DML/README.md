# Banking Transactions (DML)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create bank_db and the customers, accounts and account_transactions tables |
| 02_insert_data.sql | Insert 10 customers, 14 accounts and 15 transactions |
| 03_inserts_updates.sql | 8 business requirements: new customer, deposit, withdrawal, transfer, interest, rollback, close account |
| 04_deletions.sql | Delete unused accounts and customers safely |
| 05_queries.sql | 8 select queries |
| 06_negative_tests.sql | 8 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04, 05, 06.

## Design

- customers 1 to N accounts
- accounts 1 to N account_transactions

Assumptions:

- A bank must keep transaction history, so an account with transactions is never deleted. It is closed instead.
- Only closed, zero-balance accounts with no transactions, and customers with no accounts, may be deleted.
- Interest is 2% on active savings accounts.
- One currency is used.
- Deposits, withdrawals and transfers use START TRANSACTION and COMMIT, so the balance change and its transaction record are saved together.

## Constraint notes

- Primary keys identify each row.
- NOT NULL is on every required column.
- UNIQUE stops duplicate customer emails, phone numbers and account numbers.
- Foreign keys make sure every account has a customer and every transaction has an account.
- Foreign keys also block deleting a customer with accounts, or an account with transactions.
- CHECK (balance >= 0) is the one validation example. It stops overdrafts.
- account_type, status and txn_type are ENUMs, so only the listed values are accepted.
- A new account's balance defaults to 0, and its status defaults to 'Active'.

## DML notes

- Requirement 1 inserts the account without a balance, so the DEFAULT 0 is used. The opening deposit is then added.
- In requirement 5, both balance updates and both transaction rows are inside one transaction, so a transfer is never half done.
- Requirement 7 shows ROLLBACK: the balance drops to 140.00 inside the transaction, then returns to 640.00.
- Requirement 8 closes an account only if its balance is 0.
- In 04, the NOT IN subqueries are safe because account_transactions.account_id and accounts.customer_id are NOT NULL.
- Workbench safe update mode blocks some UPDATE and DELETE statements, so scripts 03 and 04 turn it off at the start and back on at the end.

## Expected results

After 03:

| Account | Balance | Reason |
| --- | --- | --- |
| ACC-10001 | 15300.00 | 2% interest |
| ACC-10003 | 10914.00 | Deposit 2500, then interest |
| ACC-10004 | 38760.00 | Transfer out 4000, then interest |
| ACC-10005 | 9600.00 | Transfer in 4000 |
| ACC-10006 | 250.00 | Withdrawal 1000 |
| ACC-10010 | 640.00 | Rolled back, so unchanged |
| ACC-10013 | 0.00, Closed | Closed |
| ACC-10015 | 5100.00 | New account, deposit 5000, then interest |

- Daniel Brooks: phone 555-3102, city Springfield.
- 5 new transaction rows dated 2025-10-01 to 2025-10-04.

After 04:

- Accounts ACC-10011 and ACC-10013 are deleted (2 rows).
- ACC-10008 is kept, because it has transaction history.
- Nadia Rahman is deleted (1 row).
- Row counts are 10 customers, 13 accounts and 20 transactions.

| Query | Expected |
| --- | --- |
| q1 | 13 rows |
| q2 | Fatima 48360.00, Bilal 28050.00, Grace 19125.00, Ayesha 18500.00, Daniel 10914.00, Emily 9996.00, Zainab 5100.00, Lucas 2300.00, Omar 640.00, James 250.00 |
| q3 | Savings 7 accounts, 127245.00, avg 18177.86. Current 5 accounts, 15990.00, avg 3198.00 |
| q4 | ACC-10006 250.00, ACC-10010 640.00 |
| q5 | Deposit 10 / 38440.00, Transfer In 1 / 4000.00, Transfer Out 1 / 4000.00, Withdrawal 8 / 10900.00 |
| q6 | 3 rows: Withdrawal 3000, Deposit 6000, Transfer Out 4000 |
| q7 | ACC-10002 |
| q8 | Springfield 3 / 77774.00, Greenville 2 / 30350.00, Fairview 2 / 19375.00, Riverside 2 / 15096.00, Lakeside 1 / 640.00 |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1, t2 | UNIQUE | 1062 Duplicate entry |
| t3 | NOT NULL | 1048 Column 'last_name' cannot be null |
| t4 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t5 | CHECK (overdraft) | 3819 Check constraint 'accounts_chk_1' is violated |
| t6 | ENUM | 1265 Data truncated for column 'account_type' |
| t7 | Delete blocked by FK (customer) | 1451 Cannot delete or update a parent row |
| t8 | Delete blocked by FK (history) | 1451 Cannot delete or update a parent row |

Row counts should be 10, 13 and 20 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: balances before, both requirement 7 results (140.00 then 640.00), balances after, the two changed customers, and the new transactions.
5. 04: the closed accounts list, the "2 row(s) affected" and "1 row(s) affected" lines in the Output panel, and the counts after.
6. 05: each query result, run one at a time with Ctrl+Enter.
7. 06: each error in the Output panel, run one at a time with Ctrl+Enter.
8. 06: the row counts at the end.

## Running notes

- Run 03 and 04 only once. Running 03 again would fail on the duplicate customer 11.
- To start again, run DROP DATABASE bank_db; and then 01 to 06.
- Workbench stops a script at the first error, so run 06 one statement at a time.

## Normalization

- 1NF: each column holds one value.
- 2NF: every table has a single-column key.
- 3NF: customer details are kept only in customers, and account details only in accounts.

## Limitations

| Limitation | Fix |
| --- | --- |
| Interest is added to balances without a transaction record | Insert an 'Interest' row for each account as well |
| Transaction amount is not checked to be positive | Add CHECK (amount > 0) |
| Balance and transaction history are not reconciled | Add a check query comparing balance with the sum of transactions |
| If an UPDATE fails inside a transaction, it stays open | Run ROLLBACK after any error |
| Account and transaction rows have no staff or audit user | Add created_by and created_at columns |
