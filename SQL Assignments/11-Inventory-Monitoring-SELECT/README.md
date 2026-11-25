# Inventory Monitoring (SELECT)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create inventory_db and the categories, suppliers, products and stock_movements tables |
| 02_insert_data.sql | Insert 5 categories, 4 suppliers, 16 products and 27 stock movements |
| 03_queries.sql | 12 queries: overview, low stock, high-value inventory and inactive items |
| 04_negative_tests.sql | 6 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04.

## Design

- categories 1 to N products
- suppliers 1 to N products
- products 1 to N stock_movements

Assumptions:

- Stock is held in one location.
- Low stock means quantity_on_hand is at or below reorder_level. Only Active products are checked.
- Stock value = quantity_on_hand x unit_cost. High value means more than 3000.
- "Today" is fixed as 2025-10-06. Inactive means no sale or restock in the last 90 days (since 2025-07-08), or no movement ever.
- Discontinued products are no longer reordered, but any stock left is reported as dead stock.

## Constraint notes

- Primary keys identify each row.
- NOT NULL is on every column.
- UNIQUE stops duplicate category names, supplier names, supplier emails and SKUs.
- Foreign keys make sure every product has a real category and supplier, and every movement has a real product.
- Foreign keys also block deleting a product that has movement history.
- CHECK (quantity_on_hand >= 0) is the one validation example. Stock cannot go negative.
- reorder_level defaults to 10. status is an ENUM ('Active', 'Discontinued') with default 'Active'.

## Query notes

| Topic | Queries | Technique |
| --- | --- | --- |
| Overview | q1 | 3-table JOIN, calculated column |
| Low stock | q2 to q4 | WHERE comparing two columns, calculated shortfall, GROUP BY supplier |
| High-value inventory | q5 to q8 | SUM of calculated value, GROUP BY category, ORDER BY with LIMIT, WHERE on an expression |
| Inactive items | q9 to q12 | MAX date with HAVING, LEFT JOIN IS NULL, WHERE on status, SUM over a date range |

- q2 compares two columns in the same row (quantity_on_hand <= reorder_level).
- q9 and q10 together give the full inactive list. q9 uses JOIN, so it only sees products that have moved at some point. q10 uses LEFT JOIN to find products that have never moved.

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 16 rows |
| q2 | Disinfectant Spray (short 40), Wireless Mouse (24), Gel Pen Pack (15), USB-C Dock (10), Office Chair (1) |
| q3 | USB-C Dock, Disinfectant Spray |
| q4 | TechSource Ltd 2, CleanPro Distributors 1, HomeLine Furniture 1, OfficeWorld Supplies 1 |
| q5 | 1026 units, value at cost 30922.00, value at price 47215.00 |
| q6 | Electronics 15402.00, Furniture 7000.00, Kitchen 5720.00, Office Supplies 2200.00, Cleaning 600.00 |
| q7 | Laptop 11160.00, Standing Desk 3920.00, Coffee Machine 3800.00, Monitor 3750.00, Filing Cabinet 2090.00 |
| q8 | Laptop, Standing Desk, Coffee Machine, Monitor |
| q9 | VGA Cable (2024-11-20), Stapler (2025-03-14), Filing Cabinet (2025-04-22) |
| q10 | Fax Paper Roll, Water Dispenser |
| q11 | VGA Cable 420.00, Fax Paper Roll 300.00 |
| q12 | 11 rows, from A4 Paper Ream 280 down to Standing Desk 3 |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'product_name' cannot be null |
| t3 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t4 | CHECK | 3819 Check constraint 'products_chk_1' is violated |
| t5 | ENUM | 1265 Data truncated for column 'status' |
| t6 | Delete blocked by FK | 1451 Cannot delete or update a parent row |

Row counts should be 16 products and 27 movements after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: each query result, run one at a time with Ctrl+Enter.
5. 04: each error in the Output panel, run one at a time with Ctrl+Enter.
6. 04: the row counts at the end.

## Running notes

- To start again, run DROP DATABASE inventory_db; and then 01 to 04.
- Workbench stops a script at the first error, so run 04 one statement at a time.

## Normalization

- 1NF: each column holds one value.
- 2NF: every table has a single-column key.
- 3NF: category and supplier details are stored once and referenced by key.

## Limitations

| Limitation | Fix |
| --- | --- |
| quantity_on_hand is not linked to the movements, so the two can disagree | Update stock in the same transaction as each movement, or calculate stock from movements |
| Movement quantity is not checked to be positive | Add CHECK (quantity > 0) |
| "Today" is a fixed date | Use CURDATE() - INTERVAL 90 DAY in a live system |
| One location only | Add a warehouses table and stock per warehouse |
| Each product has one supplier | Add a product_suppliers link table |
