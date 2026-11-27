# Use case 4: Visitor Management System

Source: `cases/All_Cases.docx`

**Business Problem:** Office visitors need a simple pre-registration system so security staff can verify and manage visitors arriving at the premises.

**Users / Roles:** EMPLOYEE, SECURITY_ADMIN

**Core Features:**

- Employee creates a visitor invitation with visitor name, phone, purpose and expected date.
- Employee views their invitations.
- Security admin views today's expected visitors.
- Security marks a visitor as CHECKED_IN.
- Security marks the visitor as CHECKED_OUT.
- Employee can see visitor status.

**Business Rules / Validation:** Visitor name and phone are mandatory; expected date cannot be in the past; only security users can check in/out visitors; a visitor cannot be checked out before check-in.

**Optional Bonus:** Visitor search, QR-code-style pass number, daily visitor statistics.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

