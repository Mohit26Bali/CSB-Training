# Use case 19: Simple Procurement Request System

Source: `cases/All_Cases.docx`

**Business Problem:** Employees need to request office items or software while procurement managers need to approve and track requests.

**Users / Roles:** EMPLOYEE, PROCUREMENT_MANAGER

**Core Features:**

- Employee creates a purchase request.
- Request contains item, quantity, estimated cost and justification.
- Employee tracks request status.
- Manager views pending requests.
- Manager approves or rejects the request.
- Approved requests can be marked as ORDERED and DELIVERED.

**Business Rules / Validation:** Quantity and estimated cost must be positive; only managers can approve; rejected requests cannot be ordered.

**Optional Bonus:** Budget limit validation, category filtering, spending dashboard.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

