# Use case 17: Customer Complaint Management Portal

Source: `cases/All_Cases.docx`

**Business Problem:** Customers need to raise complaints and service agents need to track, respond to and close complaints.

**Users / Roles:** CUSTOMER, SUPPORT_AGENT

**Core Features:**

- Customer securely creates a complaint.
- Customer views their complaint history.
- Support agent views open complaints.
- Agent updates status and adds resolution notes.
- Customer can see the resolution.
- Complaint follows OPEN, IN_PROGRESS, RESOLVED and CLOSED states.

**Business Rules / Validation:** Customer can only view their own complaints; only support agents can resolve complaints; resolution notes are mandatory when resolving.

**Optional Bonus:** Priority, category filtering, complaint statistics.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

