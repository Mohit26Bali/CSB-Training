# Use case 7: Product Warranty Claim System

Source: `cases/All_Cases.docx`

**Business Problem:** Customers need to raise warranty claims for purchased products while service agents need to review and process claims.

**Users / Roles:** CUSTOMER, SERVICE_AGENT

**Core Features:**

- Customer registers a purchased product.
- Customer submits a warranty claim with issue description.
- Customer views claim history.
- Service agent views pending claims.
- Agent approves or rejects the claim.
- Approved claims can be marked as IN_REPAIR and COMPLETED.

**Business Rules / Validation:** Only products with active warranty can generate claims; duplicate active claims for the same product should be prevented; rejection requires a reason.

**Optional Bonus:** Warranty expiry calculation, claim priority, product search.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

