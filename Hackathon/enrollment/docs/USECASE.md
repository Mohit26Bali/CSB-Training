# Use case 13: Online Course Enrollment and Approval System

Source: `cases/All_Cases.docx`

**Business Problem:** Employees want to enroll in internal training courses while administrators need to control course capacity and enrollment.

**Users / Roles:** EMPLOYEE, ADMIN

**Core Features:**

- Users can browse available courses.
- Employee submits an enrollment request.
- Employee views their enrolled/requested courses.
- Admin creates and manages courses.
- Admin approves or rejects enrollment requests.
- Course capacity must be respected.

**Business Rules / Validation:** A user cannot enroll in the same course twice; enrollment cannot exceed course capacity; inactive courses cannot accept enrollment.

**Optional Bonus:** Course search, category filtering, enrollment count dashboard.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

