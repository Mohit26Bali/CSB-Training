# Retail Store Schema (DDL)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create retail_db and the categories, suppliers and products tables |
| 02_insert_data.sql | Insert sample data |
| 03_alter_tables.sql | Schema changes: add column, add CHECK, add UNIQUE, modify column |
| 04_queries.sql | 8 select queries |
| 05_negative_tests.sql | 9 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04, 05.

## Design

- categories 1 to N products
- suppliers 1 to N products

Assumptions:

- Each product has one supplier. If a product can come from several suppliers, a product_suppliers link table would be needed.
- The brief does not say which ALTERs to run, so 03 shows four common ones: add a column, add a CHECK, add a UNIQUE, and modify a column.
- Prices are stored in one currency.

## Constraint notes

- Primary keys identify each row.
- NOT NULL is on every required column. description is optional.
- UNIQUE stops duplicate category names, supplier names and SKUs. Supplier email is made UNIQUE later by ALTER.
- Foreign keys make sure a category and supplier exist before a product uses them.
- Foreign keys also block deleting a category or supplier that still has products. This is MySQL's default.
- CHECK (unit_price > 0) is the one validation example. It is added by ALTER.

## ALTER notes

- stock_quantity is added as NOT NULL DEFAULT 0. Here 0 is a real, correct value (no stock), so a default is safe.
- The existing products are then updated with their real stock. Chocolate Biscuits is not updated, so it keeps 0 and becomes the out-of-stock example.
- ADD CHECK and ADD UNIQUE check the existing rows first. If any row broke the rule, the ALTER would fail.
- MODIFY COLUMN replaces the whole column definition, so NOT NULL has to be written again. Otherwise phone would become nullable.

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 10 products |
| q2 | Beverages 3, Dairy 2, Household 2, Snacks 2, Personal Care 1, Frozen Foods 0 |
| q3 | Household 5.89, Personal Care 5.49, Dairy 3.24, Snacks 2.09, Beverages 1.76 |
| q4 | CleanHome 747.05, FreshFields 715.80, SnackWorld 538.70, PureDairy 214.15 |
| q5 | Cola, Mineral Water, Milk, Potato Chips, Dishwashing Liquid |
| q6 | Chocolate Biscuits, SnackWorld Wholesale |
| q7 | Lakeside Imports |
| q8 | Personal Care |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1, t2 | UNIQUE | 1062 Duplicate entry |
| t3 | NOT NULL | 1048 Column 'product_name' cannot be null |
| t4 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t5 | Delete blocked by FK | 1451 Cannot delete or update a parent row |
| t6 | CHECK from ALTER | 3819 Check constraint 'products_chk_1' is violated (run after 03) |
| t7 | UNIQUE from ALTER | 1062 Duplicate entry for key 'suppliers.email' (run after 03) |
| t8 | NOT NULL from ALTER | 1048 Column 'stock_quantity' cannot be null (run after 03) |
| t9 | NOT NULL kept by MODIFY | 1048 Column 'phone' cannot be null (run after 03) |

Row counts should be 6, 5 and 10 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: both DESCRIBEs before, both DESCRIBEs after, SHOW CREATE TABLE products, and the stock list.
5. 04: each query result, run one at a time with Ctrl+Enter.
6. 05: each error in the Output panel, run one at a time with Ctrl+Enter.
7. 05: the row counts at the end.

## Running notes

- Workbench stops a script at the first error, so run 05 one statement at a time.
- 01 has no DROP DATABASE. To start again, run DROP DATABASE retail_db; manually first.
- Every UPDATE and DELETE filters on the primary key, so Workbench safe update mode allows them.

## Normalization

- 1NF: each column holds one value.
- 2NF: every table has a single-column key.
- 3NF: category and supplier details are kept in their own tables, not repeated in products.

## Limitations

| Limitation | Fix |
| --- | --- |
| A product can have only one supplier | Add a product_suppliers link table |
| Stock can go negative | Add CHECK (stock_quantity >= 0) |
| No price history | Add a price_history table |
| Stock is one number with no warehouse or location | Add an inventory table per location |
| Email and phone format are not checked | Validate in the application |
