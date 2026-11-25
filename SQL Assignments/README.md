# SQL Assignments

Fifteen MySQL assignments, each in its own folder with its scripts, a README and a database of its own:

| # | Assignment | Type | Database |
|---|---|---|---|
| 01 | [Organization Database](01-Organization-Database-DDL) | DDL | `org_db` |
| 02 | [University Management](02-University-Management-DDL) | DDL | `university_db` |
| 03 | [Retail Store](03-Retail-Store-DDL) | DDL | `retail_db` |
| 04 | [Employee Data Management](04-Employee-Data-Management-DML) | DML | `employee_dml_db` |
| 05 | [Banking Transactions](05-Banking-Transactions-DML) | DML | `bank_db` |
| 06 | [Library Management](06-Library-Management-DML) | DML | `library_db` |
| 07 | [Employee Reporting](07-Employee-Reporting-SELECT) | SELECT | `employee_report_db` |
| 08 | [Sales Analytics](08-Sales-Analytics-SELECT) | SELECT | `sales_db` |
| 09 | [Customer Insights](09-Customer-Insights-SELECT) | SELECT | `customer_insights_db` |
| 10 | [HR Dashboard](10-HR-Dashboard-SELECT) | SELECT | `hr_dashboard_db` |
| 11 | [Inventory Monitoring](11-Inventory-Monitoring-SELECT) | SELECT | `inventory_db` |
| 12 | [Academic Reports](12-Academic-Reports-SELECT) | SELECT | `academic_db` |
| 13 | [Multi-Table Queries](13-Multi-Table-Queries-SELECT) | SELECT | `multi_table_db` |
| 14 | [Advanced Reporting](14-Advanced-Reporting-SELECT) | SELECT | `advanced_report_db` |
| 15 | [Capstone: HR Management](15-Capstone-HR-Management) | Capstone | `hr_management_db` |

Run order inside each folder is the file number order (`01_create_tables.sql`, `02_insert_data.sql`, ...). Each folder's README lists its files and what they do. The `*_negative_tests.sql` scripts are **supposed to fail**: each statement breaks a rule on purpose and a comment names the MySQL error it should give.

## Checked

All 15 assignments were run in full on MySQL 8.4: every script ran without an unexpected error, and all **108 negative tests** failed with exactly the error number their comment predicts.

## SQL Explorer

[`sql-explorer/`](sql-explorer) is a web app (React + Node/Express) that loads every assignment's database with its seed data, so you can browse the tables, run the queries, watch the INSERT / UPDATE / DELETE scripts work (nothing is saved) and try the negative tests, all from the browser. See its README.
