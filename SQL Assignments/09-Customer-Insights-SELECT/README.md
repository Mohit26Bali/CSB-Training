# Customer Insights (SELECT)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create customer_insights_db and the customers, categories, products and purchases tables |
| 02_insert_data.sql | Insert 12 customers, 4 categories, 10 products and 32 purchases |
| 03_queries.sql | 14 queries on purchasing behavior |
| 04_negative_tests.sql | 6 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04.

## Design

- customers 1 to N purchases
- products 1 to N purchases
- categories 1 to N products

Assumptions:

- Customers are individual shoppers of a retail store with online and in-store channels.
- Each purchase row is one product line bought by one customer on one date.
- Purchase data covers January to June 2025. "Inactive" means no purchase since 2025-04-01.
- unit_price is the price paid, so it can be below list price.
- Purchase amount = quantity x unit_price.

## Constraint notes

- Primary keys identify each row.
- NOT NULL is on every column.
- UNIQUE stops duplicate customer emails, category names and product names.
- Foreign keys make sure every purchase has a real customer and product, and every product has a real category.
- Foreign keys also block deleting a customer who has purchases.
- CHECK (quantity > 0) is the one validation example.
- loyalty_tier, channel and payment_method are ENUMs. loyalty_tier defaults to 'Standard'.

## Query notes

| Topic | Queries | Technique |
| --- | --- | --- |
| Spending per customer | q1, q2 | SUM, AVG, GROUP BY, ORDER BY DESC, LIMIT |
| Purchase frequency | q3 to q7 | HAVING COUNT, MIN / MAX dates, HAVING on MAX date, LEFT JOIN IS NULL |
| Customer segments | q8, q9 | GROUP BY tier and city, COUNT(DISTINCT) |
| Shopping preferences | q10 to q14 | GROUP BY channel, payment and category, several WHERE filters with AND, monthly grouping |

- WHERE filters single purchases (q13). HAVING filters customers after their purchases are grouped (q3, q4, q6).
- COUNT(DISTINCT c.customer_id) counts each customer once, even when they have many purchase rows.
- q6 uses HAVING MAX(purchase_date) < '2025-04-01'. That finds customers whose most recent purchase is before April, which is different from a WHERE filter on single purchases.

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 11 customers, from Fatima Malik 625.00 (5 purchases) down to Sophie Clark 30.00 |
| q2 | Fatima Malik 625.00, Ayesha Karim 597.00, Grace Lee 511.00 |
| q3 | Ayesha 6, Fatima 5, Daniel Brooks 4, Emily Chen 4, Grace Lee 4 |
| q4 | Lucas Silva, Bilal Hussain, Sophie Clark |
| q5 | 11 rows, from Daniel Brooks (last 2025-06-25) to Lucas Silva (last 2025-01-18) |
| q6 | Lucas Silva, James Carter, Bilal Hussain |
| q7 | Hamza Iqbal |
| q8 | Gold 3 customers / 15 purchases / 1733.00, Silver 3 / 9 / 570.00, Standard 5 / 8 / 526.00 |
| q9 | Springfield 1252.00, Fairview 605.00, Riverside 540.00, Lakeside 271.00, Greenville 161.00 |
| q10 | Online 20 / 1975.00, In-Store 12 / 854.00 |
| q11 | Card 19, Wallet 7, Cash 6 |
| q12 | Clothing 8 customers, Electronics 7, Home 5 (555.00), Beauty 5 (480.00) |
| q13 | 6 rows: Ayesha, Grace, Fatima (Smart Watches), Grace (Cookware), Fatima (Perfume), Grace (Earbuds) |
| q14 | Jan 7 customers, Feb 5, Mar 5, Apr 4, May 3, Jun 6 |

Total spend across all purchases is 2829.00.

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'city' cannot be null |
| t3 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t4 | CHECK | 3819 Check constraint 'purchases_chk_1' is violated |
| t5 | ENUM | 1265 Data truncated for column 'loyalty_tier' |
| t6 | Delete blocked by FK | 1451 Cannot delete or update a parent row |

Row counts should be 12 customers and 32 purchases after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: each query result, run one at a time with Ctrl+Enter.
5. 04: each error in the Output panel, run one at a time with Ctrl+Enter.
6. 04: the row counts at the end.

## Running notes

- To start again, run DROP DATABASE customer_insights_db; and then 01 to 04.
- Workbench stops a script at the first error, so run 04 one statement at a time.

## Normalization

- 1NF: each column holds one value.
- 2NF: every table has a single-column key.
- 3NF: customer, product and category details are stored once and referenced by key.

## Limitations

| Limitation | Fix |
| --- | --- |
| A basket of several products is stored as separate purchase rows | Add an orders table and an order_items table |
| "Inactive" uses a fixed date | Use CURDATE() - INTERVAL 90 DAY in a live system |
| Loyalty tier is set by hand, not from spend | Recalculate tiers from spend with an UPDATE |
| No returns or refunds | Add a returns table |
| Unit price is not checked to be positive | Add CHECK (unit_price > 0) |
