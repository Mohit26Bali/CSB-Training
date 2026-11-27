# Use case 12: Leave Request and Approval Portal

Source: `cases/All_Cases.docx`

**Business Problem:** Employees need a simple system to apply for leave and managers need to approve or reject requests without relying on email.

**Users / Roles:** EMPLOYEE, MANAGER

**Core Features:**

- Employee logs in and views leave balance.
- Employee submits leave request with type, start date, end date and reason.
- Employee views submitted requests and their status.
- Manager views pending leave requests.
- Manager approves or rejects requests with an optional reason.
- Approved leave updates the employee's available leave balance.

**Business Rules / Validation:** End date cannot precede start date; leave days must be positive; overlapping approved leave should be prevented; employee cannot approve their own request.

**Optional Bonus:** Calendar view, leave search/filter, different leave types.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

