# Use case 18: Event Registration and Approval System

Source: `cases/All_Cases.docx`

**Business Problem:** Organizations conduct internal events and need a system to publish events, accept registrations and control participant capacity.

**Users / Roles:** EMPLOYEE, EVENT_ADMIN

**Core Features:**

- Admin creates and publishes events.
- Employee browses upcoming events.
- Employee registers for an event.
- Employee views their registrations.
- Admin views registered participants.
- Registration is blocked when the event reaches capacity.

**Business Rules / Validation:** Past events cannot accept registration; duplicate registration is not allowed; event capacity must not be exceeded.

**Optional Bonus:** Event search/filter, participant count dashboard, waitlist.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

