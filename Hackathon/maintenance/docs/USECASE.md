# Use case 10: Community Maintenance Request System

Source: `cases/All_Cases.docx`

**Business Problem:** Residents of an apartment community need to report maintenance issues while facility managers need to assign and resolve them.

**Users / Roles:** RESIDENT, FACILITY_MANAGER

**Core Features:**

- Resident creates a maintenance request with category, description and location.
- Resident views their requests.
- Facility manager views open requests.
- Manager assigns a request and updates status.
- Manager adds resolution notes when completing the request.
- Request follows OPEN, ASSIGNED, IN_PROGRESS, RESOLVED and CLOSED.

**Business Rules / Validation:** Description and location are mandatory; only facility managers can assign/change status; resolution notes are mandatory for RESOLVED status.

**Optional Bonus:** Priority, category dashboard, search/filter, aging indicator.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

