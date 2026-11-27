# Use case 8: Hotel Room Booking System

Source: `cases/All_Cases.docx`

**Business Problem:** Customers need to search rooms and make reservations while hotel administrators manage room availability and booking status.

**Users / Roles:** CUSTOMER, HOTEL_ADMIN

**Core Features:**

- Admin creates rooms with room type and price.
- Customer views available rooms.
- Customer selects check-in and check-out dates.
- Customer creates a booking.
- Customer views booking history.
- Admin confirms or cancels bookings.

**Business Rules / Validation:** Check-out must be after check-in; dates cannot be in the past; the same room cannot have overlapping confirmed bookings; cancellation is restricted by status.

**Optional Bonus:** Room type filtering, booking cost calculation, availability calendar.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

