# Use case 20: Appointment Booking and Approval System

Source: `cases/All_Cases.docx`

**Business Problem:** Employees or customers need to book appointments with service providers while administrators need to control available slots.

**Users / Roles:** CUSTOMER, SERVICE_PROVIDER/ADMIN

**Core Features:**

- Admin creates available appointment slots.
- Customer views available slots.
- Customer books a slot.
- Customer views upcoming appointments.
- Provider views scheduled appointments.
- Provider confirms or cancels an appointment.

**Business Rules / Validation:** A slot cannot be double-booked; past slots cannot be booked; only the provider/admin can confirm or cancel appointments.

**Optional Bonus:** Calendar UI, appointment search, cancellation reason.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

- Common Technology and Architecture Requirements
- Spring Core: Use IoC, dependency injection, stereotypes such as @Service and @Repository, and constructor injection.
- Spring Boot: Use Spring Boot, configuration through application.properties/yml, auto-configuration and an embedded server.
- Spring MVC / REST: Use @RestController, request mappings, request/response DTOs, path variables, request bodies and appropriate HTTP methods/status codes.
- Spring Data JPA: Use entities, relationships, JpaRepository and derived/custom queries where needed.
- Spring Security: Implement login/authentication, password encoding and role-based authorization. Employees/customers must not access admin APIs.
- Database: Use MySQL or another relational database with primary keys, foreign keys, unique constraints and suitable validation.
- Validation: Validate both input data and business rules at the backend. Display meaningful errors in React.
- Architecture: Postman -&gt;  REST Controller → Service → Repository → Database.
- Deliverables: Source code, database setup, README, API documentation, sample credentials and a working demonstration.
- Evaluation Criteria
- Evaluation Area
- Marks
- Spring / Spring Boot / Dependency Injection
- 10
- Spring MVC / REST APIs
- 15
- Spring Data JPA / Database
- 15
- Spring Security / Role-based Authorization
- 15
- Business Logic / Validation
- 10
- React Components / Routing / Forms
- 10
- React–Spring Integration
- 10
- End-to-End Working Flow
- 10
- Code Quality / README / Demo
- 5
