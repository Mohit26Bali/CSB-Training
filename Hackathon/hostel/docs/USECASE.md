# Use case 3: Hostel Room Allocation System

Source: `cases/All_Cases.docx`

**Business Problem:** Students need to request hostel rooms and hostel administrators need to allocate available rooms and track occupancy.

**Users / Roles:** STUDENT, HOSTEL_ADMIN

**Core Features:**

- Admin creates rooms with room number, capacity and type.
- Student views available rooms.
- Student submits a room request.
- Admin views pending requests.
- Admin approves a request and allocates a room.
- Student can view their current room allocation.

**Suggested JPA Entities:** User, Room, RoomRequest, RoomAllocation

**Optional Bonus:** Room type filtering, occupancy dashboard, waiting list.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

