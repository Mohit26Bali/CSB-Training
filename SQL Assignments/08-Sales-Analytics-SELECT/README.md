# Sales Analytics (SELECT)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create sales_db and the categories, products, customers, orders and order_items tables |
| 02_insert_data.sql | Insert 4 categories, 13 products, 8 customers, 18 orders and 37 order items |
| 03_queries.sql | 12 queries: sales summaries, top products and management reports |
| 04_negative_tests.sql | 7 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04.

## Design

- categories 1 to N products
- customers 1 to N orders
- orders N to M products, through order_items

Assumptions:

- Customers are businesses, grouped into four regions.
- Sales data covers January to June 2025.
- Only Completed orders count as sales. Cancelled orders are reported separately in q11.
- order_items stores the price actually charged, because it can differ from the product's list price (discounts).
- Revenue = quantity x unit_price.

## Constraint notes

- Primary keys identify each row. order_items uses (order_id, product_id), so a product appears once per order.
- NOT NULL is on every column.
- UNIQUE stops duplicate category names, SKUs, company names and contact emails.
- Foreign keys make sure every order has a customer, and every order item has a real order and product.
- Foreign keys also block deleting a product that has been sold.
- CHECK (quantity > 0) is the one validation example.
- status is an ENUM ('Completed', 'Cancelled') with default 'Completed'.

## Query notes

| Topic | Queries | Aggregates used |
| --- | --- | --- |
| Sales summaries | q1 to q3 | COUNT(DISTINCT), SUM, GROUP BY month and category |
| Top-performing products | q4 to q7 | SUM with ORDER BY DESC and LIMIT, AVG / MIN / MAX, HAVING, LEFT JOIN IS NULL |
| Management reports | q8 to q12 | SUM by region and customer, HAVING on totals, LEFT JOIN IS NULL |

- COUNT(DISTINCT o.order_id) is needed because joining order_items repeats each order once per item.
- DATE_FORMAT(order_date, '%Y-%m') turns a date into a month such as 2025-03 for grouping.
- Average order value in q1 is total revenue divided by the number of orders.

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 16 orders, 676 units, revenue 38057.50, average order 2378.59 |
| q2 | Jan 9495.00, Feb 8060.00, Mar 3365.00, Apr 6100.00, May 3007.50, Jun 8030.00 |
| q3 | Electronics 24390.00, Furniture 8440.00, Stationery 4137.50, Appliances 1090.00 |
| q4 | Laptop 19190.00, Office Chair 4820.00, Monitor 2860.00, Notebook Set 2400.00, Standing Desk 2100.00 |
| q5 | Notebook Set 200, A4 Paper Ream 175, Gel Pen Pack 150 |
| q6 | Laptop (list 850, avg 837.50, min 820), Office Chair (list 180, avg 178.75, min 175) |
| q7 | Air Purifier |
| q8 | North 25460.00, South 6890.00, East 5167.50, West 540.00 |
| q9 | Pioneer Tech Labs 17410.00, Brightside Consulting 8050.00 |
| q10 | Orders 4 (7520.00), 1 (5475.00), 17 (5130.00), 11 (4760.00), 2 (2060.00) |
| q11 | 2 cancelled orders, 2130.00 |
| q12 | Lakeview Startup Hub |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'product_name' cannot be null |
| t3 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t4 | CHECK | 3819 Check constraint 'order_items_chk_1' is violated |
| t5 | PRIMARY KEY | 1062 Duplicate entry '1-1' for key 'PRIMARY' |
| t6 | ENUM | 1265 Data truncated for column 'status' |
| t7 | Delete blocked by FK | 1451 Cannot delete or update a parent row |

Row counts should be 13, 18 and 37 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: each query result, run one at a time with Ctrl+Enter.
5. 04: each error in the Output panel, run one at a time with Ctrl+Enter.
6. 04: the row counts at the end.

## Running notes

- To start again, run DROP DATABASE sales_db; and then 01 to 04.
- Workbench stops a script at the first error, so run 04 one statement at a time.

## Normalization

- 1NF: each column holds one value. Order lines are in their own table.
- 2NF: in order_items, quantity and unit_price depend on the whole key (this product in this order).
- 3NF: product, customer and category details are stored once and referenced by key.
- unit_price in order_items is not duplication. It records the price at the time of sale.

## Limitations

| Limitation | Fix |
| --- | --- |
| Revenue is recalculated every time a report runs | Store order totals, or use a summary table |
| No tax, shipping or returns | Add columns or tables for them |
| Unit price is not checked to be positive | Add CHECK (unit_price > 0) |
| No sales rep, so no performance report per salesperson | Add a sales_reps table and a rep_id on orders |
| Top-N results do not handle ties | Add a second sort column |
