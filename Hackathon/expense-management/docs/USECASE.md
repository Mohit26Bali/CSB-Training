# Use case 11: Smart Employee Expense Management System

Source: `cases/All_Cases.docx`

**Business Problem:** Employees currently submit business expenses through email or spreadsheets. Managers need a centralized way to review and approve or reject expenses.

**Users / Roles:** EMPLOYEE, MANAGER

**Core Features:**

- Secure login with role-based access.
- Employee submits an expense with date, category, amount and description.
- Employee views only their own expenses and status.
- Manager views pending expenses.
- Manager approves or rejects an expense; rejection requires a reason.
- Expense status flows from PENDING to APPROVED or REJECTED.

**Business Rules / Validation:** Amount must be greater than zero; date cannot be in the future; only managers can approve/reject; processed expenses cannot be processed again.

**Optional Bonus:** Dashboard statistics, search by status/category, JWT authentication.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

