"""python tools/readmes/hostel.py hostel/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Hostel Room Allocation System",
    "summary": "Wardens manage rooms and allocate beds; students browse free rooms, send a request and follow it from the waiting list to "
               "their room. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8103, "frontend_port": 5103, "db": "hostel_allocation", "package": "hostel",
    "logins": [("`warden`", "`admin123`", "HOSTEL_ADMIN"), ("`mahesh.kulkarni`", "`admin123`", "HOSTEL_ADMIN"),
               ("`aarav.sharma` (no room, no request: try the whole flow)", "`student123`", "STUDENT"),
               ("`diya.patel` (has a room), `rohan.mehta` (pending request), … (30 students)", "`student123`", "STUDENT")],
    "features": [
        "**Student:** see rooms with a free bed (filter by type, with a photo and beds left); request a room with an optional preferred "
        "type and note; see the position on the waiting list; cancel a pending request; see the current room allocation and the reason for any rejection.",
        "**Warden:** create and edit rooms (number, floor, type, capacity, open or closed for maintenance); see every room with its occupants; "
        "review requests (pending ones are the waiting list, oldest first); allocate a room of choice or reject with a reason; "
        "vacate a room when a student leaves; an occupancy dashboard by room type.",
        "Room type filtering, occupancy dashboard and waiting list (the case's bonus features).",
    ],
    "rules": [
        ("Room numbers are unique (case and spaces ignored)", "`400` field error"),
        ("Capacity is 1 to 12 beds and can never be lowered below the number of residents", "`400` field error"),
        ("A student can have only one pending request, and none while they already have a room", "`409`"),
        ("A room can only be given while it is open and has a free bed", "`400` (closed) / `409` (full)"),
        ("A student can only ever live in one room at a time", "`409`"),
        ("A decided request cannot be decided again; only a pending request can be cancelled, and only by its owner", "`409` / `403`"),
        ("Rejecting needs a reason, which the student sees", "`400` field error"),
        ("Only wardens manage rooms and decide; only students request", "`403`"),
    ],
    "concurrency": "approving locks the room row, then the student row, and only then loads the request (under `READ_COMMITTED`), so two wardens "
                   "cannot fill the last bed twice, one request cannot be approved into two rooms, and a double-clicked request creates one request. "
                   "The tests check all three with parallel threads.",
    "notes": [
        "Students only see how many beds are free in a room, never who lives there. Only the warden sees occupants.",
        "Rooms under maintenance (closed) are left out of the free-bed list and of the occupancy percentage.",
    ],
    "api": [
        ("GET", "/api/rooms?type=", "any", "Active rooms with a free bed"),
        ("GET", "/api/rooms/types", "any", "Room types"),
        ("POST", "/api/requests", "STUDENT", "Ask for a room `{preferredType?, note?}`"),
        ("GET", "/api/requests/my", "STUDENT", "Own requests with waiting-list position"),
        ("DELETE", "/api/requests/{id}", "STUDENT", "Cancel a pending request"),
        ("GET", "/api/allocation/my", "STUDENT", "Current room (`204` when none)"),
        ("GET", "/api/admin/rooms?type=", "HOSTEL_ADMIN", "All rooms with occupants"),
        ("POST", "/api/admin/rooms", "HOSTEL_ADMIN", "Create a room `{number, floor, type, capacity, active?}`"),
        ("PUT", "/api/admin/rooms/{id}", "HOSTEL_ADMIN", "Edit a room"),
        ("GET", "/api/admin/requests?status=", "HOSTEL_ADMIN", "Requests (pending = waiting list)"),
        ("PUT", "/api/admin/requests/{id}/approve", "HOSTEL_ADMIN", "Allocate `{roomId}`"),
        ("PUT", "/api/admin/requests/{id}/reject", "HOSTEL_ADMIN", "Reject `{reason}`"),
        ("GET", "/api/admin/allocations?activeOnly=", "HOSTEL_ADMIN", "Residents (current or all)"),
        ("PUT", "/api/admin/allocations/{id}/vacate", "HOSTEL_ADMIN", "The student leaves the room"),
        ("GET", "/api/admin/dashboard", "HOSTEL_ADMIN", "Occupancy totals and by room type"),
    ],
    "controllers": "AuthController, RoomController (student), AdminController",
    "services": "RoomService (rooms), HostelService (requests, allocation, locking, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository (row lock), RoomRepository (row lock), RoomRequestRepository, RoomAllocationRepository",
    "entities": "User, Room, RoomRequest, RoomAllocation, Role, RoomType, RequestStatus",
    "tables": "`users`, `rooms` (unique number), `room_requests` (student, status, decision, room) and `room_allocations` (room, student, vacated-at).",
    "tests": "25 tests on in-memory H2 (no MySQL needed): room rules, waiting list, approval, vacating, dashboard, roles, parallel approvals",
    "collection": "Hostel.postman_collection.json",
    "postman": "55 requests (84 assertions). It registers fresh students and creates uniquely numbered rooms each run, so it can be run many times",
    "limits": [
        "A student cannot request a move once allocated; the warden vacates the room first.",
        "No gender-based blocks or fee tracking.",
        "Filtering and the waiting list are computed in memory (Java Streams), which is fine for one hostel.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
