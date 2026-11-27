# Use case 6: Digital Parking Slot Management System

Source: `cases/All_Cases.docx`

**Business Problem:** Organizations need to allocate limited parking slots to employees and allow users to view their parking allocation.

**Users / Roles:** EMPLOYEE, PARKING_ADMIN

**Core Features:**

- Admin creates parking slots and marks their status.
- Employee views available slots.
- Employee requests a parking slot.
- Admin approves a request and allocates a slot.
- Employee views current allocation.
- Admin can release a slot when the allocation ends.

**Business Rules / Validation:** A slot can have only one active allocation; an employee can have only one active slot; only available slots can be allocated.

**Optional Bonus:** Vehicle number registration, floor-wise filtering, occupancy dashboard.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

