# Advanced Reporting (SELECT)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create advanced_report_db and the categories, products, customers, orders and order_items tables |
| 02_insert_data.sql | Insert 4 categories, 12 products, 8 customers, 15 orders and 28 order items |
| 03_queries.sql | 13 business questions answered with nested queries |
| 04_negative_tests.sql | 6 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04.

## Design

- categories 1 to N products
- customers 1 to N orders
- orders N to M products, through order_items

Assumptions:

- The brief asks for nested queries, so subqueries are used here. CTEs (WITH) and window functions are still avoided.
- Most subqueries are non-correlated: they run once on their own. Two queries (q12, q13) are correlated and are labelled as such.
- Revenue = quantity x the product's unit_price. Prices are assumed not to change.

## Constraint notes

- Primary keys identify each row. order_items uses (order_id, product_id), so a product appears once per order.
- NOT NULL is on every column.
- UNIQUE stops duplicate category names, product names and customer emails.
- Foreign keys make sure every product has a category, every order has a customer, and every order item has a real order and product.
- Foreign keys also block deleting a customer who has orders.
- CHECK (unit_price > 0) is the one validation example.

## Query notes

| Type of subquery | Queries | What it returns |
| --- | --- | --- |
| Scalar subquery | q1, q2, q3 | One value, used in WHERE or SELECT |
| List subquery (IN / NOT IN) | q4, q5, q6 | A list of ids |
| Derived table (subquery in FROM) | q7 to q11 | A temporary result that is then averaged or filtered |
| Correlated subquery (EXISTS, per-row) | q12, q13 | Runs once for each outer row |

- A non-correlated subquery runs once, and its result is used by the outer query.
- A correlated subquery refers to the outer query (p.category_id, c.customer_id), so it runs once for each outer row.
- A derived table must have an alias (AS ot, AS cs, AS u). MySQL gives an error without one.
- q7 to q11 compare each total with the average of all totals. AVG(SUM(...)) is not allowed, so the totals are calculated in a derived table first, then averaged.
- q9 uses HAVING ... = MAX instead of ORDER BY ... LIMIT 1. If two products tie for first place, both are shown.
- q4 uses NOT IN, which is safe here because order_items.product_id cannot be NULL. q13 uses NOT EXISTS, which is safe even when NULLs are possible.

## Expected results

Total revenue is 8629.00. The average customer spend is 1232.71, and the average order value is 575.27.

| Query | Expected |
| --- | --- |
| q1 | Laptop 850, Standing Desk 420, Monitor 220, Air Purifier 210 (average price is 192.00) |
| q2 | Standing Desk 420.00 |
| q3 | Fatima 2325.00 (26.94%), Ayesha 2270.00 (26.31%), Bilal 2112.00 (24.48%), James 715.00 (8.29%), Daniel 600.00 (6.95%), Emily 355.00 (4.11%), Omar 252.00 (2.92%) |
| q4 | Air Purifier |
| q5 | Ayesha Karim, Bilal Hussain, Daniel Brooks, Emily Chen, Fatima Malik, James Carter |
| q6 | Ayesha Karim, Bilal Hussain |
| q7 | Orders 6 (1940.00), 11 (1200.00), 12 (1000.00), 1 (900.00), 3 (860.00) |
| q8 | Fatima Malik 2325.00, Ayesha Karim 2270.00, Bilal Hussain 2112.00 |
| q9 | Gel Pen Pack 55 |
| q10 | Electronics 5035.00, Furniture 2540.00 |
| q11 | Gel Pen Pack 55, Notebook Set 31 |
| q12 | Appliances: Air Purifier 210, Electronics: Laptop 850, Furniture: Standing Desk 420, Stationery: Desk Organizer 18 |
| q13 | Grace Lee |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'product_name' cannot be null |
| t3 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t4 | CHECK | 3819 Check constraint 'products_chk_1' is violated |
| t5 | PRIMARY KEY | 1062 Duplicate entry '1-1' for key 'PRIMARY' |
| t6 | Delete blocked by FK | 1451 Cannot delete or update a parent row |

Row counts should be 12 products, 8 customers and 28 order items after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: each query result, run one at a time with Ctrl+Enter.
5. 04: each error in the Output panel, run one at a time with Ctrl+Enter.
6. 04: the row counts at the end.

## Running notes

- To start again, run DROP DATABASE advanced_report_db; and then 01 to 04.
- Workbench stops a script at the first error, so run 04 one statement at a time.
- The queries were checked by running them on the same data in SQLite. Run them in MySQL yourself and compare with the table above.

## Normalization

- 1NF: each column holds one value. Order lines are in their own table.
- 2NF: quantity depends on the whole key of order_items.
- 3NF: category, product and customer details are stored once and referenced by key.

## Limitations

| Limitation | Fix |
| --- | --- |
| Revenue uses today's product price, so a price change rewrites history | Store unit_price in order_items at the time of sale |
| The same derived table is written twice in q7 | A CTE (WITH) would name it once, but CTEs were kept out to keep the SQL basic |
| Correlated subqueries run once per row, which is slow on large tables | Rewrite with JOIN and GROUP BY, or add indexes |
| Quantity is not checked to be positive | Add CHECK (quantity > 0) |
