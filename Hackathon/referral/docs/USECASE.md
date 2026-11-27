# Use case 9: Internal Job Referral Portal

Source: `cases/All_Cases.docx`

**Business Problem:** Employees need to refer candidates for open positions and recruiters need to review referrals and update their status.

**Users / Roles:** EMPLOYEE, RECRUITER

**Core Features:**

- Recruiter creates and publishes job openings.
- Employee views open positions.
- Employee submits a candidate referral.
- Employee tracks referral status.
- Recruiter views pending referrals.
- Recruiter updates referral through SUBMITTED, SCREENING, INTERVIEW, SELECTED or REJECTED.

**Business Rules / Validation:** Only published jobs accept referrals; a candidate cannot be referred twice for the same job; only recruiters can change referral status.

**Optional Bonus:** Search jobs by skill, recruiter dashboard, referral count.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

