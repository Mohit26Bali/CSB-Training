# Use case 14: IT Help Desk Ticket Management System

Source: `cases/All_Cases.docx`

**Business Problem:** Employees need to report technical issues, and support engineers need a centralized queue to track and resolve them.

**Users / Roles:** EMPLOYEE, SUPPORT_AGENT

**Core Features:**

- Employee creates a support ticket.
- Ticket contains title, description, category and priority.
- Employee views their own tickets.
- Support agent views open tickets.
- Support agent assigns and updates ticket status.
- Ticket can move through OPEN, IN_PROGRESS, RESOLVED and CLOSED.

**Business Rules / Validation:** Title and description are mandatory; only support agents can change assignment/status; closed tickets cannot be reopened unless explicitly allowed.

**Optional Bonus:** Priority filtering, ticket search, SLA indicator.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

