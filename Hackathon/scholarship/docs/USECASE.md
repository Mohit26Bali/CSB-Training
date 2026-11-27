# Use case 5: Scholarship Application Management System

Source: `cases/All_Cases.docx`

**Business Problem:** Students need to apply for scholarships and administrators need to review applications and approve or reject them based on eligibility information.

**Users / Roles:** STUDENT, SCHOLARSHIP_ADMIN

**Core Features:**

- Student views available scholarship programs.
- Student submits an application with academic and financial information.
- Student tracks application status.
- Admin views submitted applications.
- Admin approves or rejects applications with a reason.
- Application status is displayed to the student.

**Business Rules / Validation:** A student cannot apply twice to the same scholarship; mandatory eligibility fields must be provided; only admins can approve/reject.

**Optional Bonus:** Eligibility score calculation, filtering by scholarship, application statistics.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

