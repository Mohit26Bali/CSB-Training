# Use case 15: Asset Allocation and Return System

Source: `cases/All_Cases.docx`

**Business Problem:** Organizations need to track laptops, monitors and other IT assets assigned to employees and ensure assets are returned.

**Users / Roles:** EMPLOYEE, ASSET_ADMIN

**Core Features:**

- Admin creates and manages assets.
- Admin assigns an available asset to an employee.
- Employee can view assets assigned to them.
- Employee raises a return request.
- Admin accepts the return and changes asset status to AVAILABLE.
- System should show asset assignment history.

**Business Rules / Validation:** Only available assets can be assigned; an asset cannot be assigned to two employees simultaneously; returned assets become AVAILABLE.

**Optional Bonus:** Asset search by serial number, asset condition tracking.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

