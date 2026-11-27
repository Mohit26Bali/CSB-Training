# Use case 2: Vehicle Service Booking System

Source: `cases/All_Cases.docx`

**Business Problem:** Vehicle owners need to book service appointments and service-center staff need to manage bookings and update service status.

**Users / Roles:** CUSTOMER, SERVICE_ADMIN

**Core Features:**

- Customer registers a vehicle.
- Customer selects a service type and available date.
- Customer creates a service booking.
- Customer views upcoming and previous service bookings.
- Service admin views scheduled bookings.
- Admin updates status from BOOKED to IN_SERVICE to COMPLETED.

**Business Rules / Validation:** Vehicle number must be unique; booking date cannot be in the past; a vehicle cannot have overlapping bookings; only admins can change service status.

**Optional Bonus:** Service history, estimated cost, vehicle search.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

