"""python tools/readmes/parking.py parking/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Digital Parking Slot Management System",
    "summary": "Admins manage the slots and allocate them; employees see free slots on a floor map, request one with their vehicle "
               "number and follow the request until they have a slot. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8106, "frontend_port": 5106, "db": "parking_slots", "package": "parking",
    "logins": [("`parkadmin`", "`admin123`", "PARKING_ADMIN"), ("`lata.deshmukh`", "`admin123`", "PARKING_ADMIN"),
               ("`aarav.sharma` (no slot, no request: try the whole flow)", "`employee123`", "EMPLOYEE"),
               ("`diya.patel` (has a slot), `rohan.mehta` (pending request), … (30 employees)", "`employee123`", "EMPLOYEE")],
    "features": [
        "**Employee:** see the free slots as a colour-coded floor map (filter by floor); request a slot with a vehicle number, an optional "
        "preferred floor and a note; see the position in the queue; cancel a pending request; see the allocated slot and vehicle, "
        "or the reason a request was rejected.",
        "**Parking admin:** create and edit slots (code, floor, available / reserved / closed); the whole lot as a clickable map; review "
        "requests (pending ones are the queue, oldest first) and allocate a slot by clicking it on the map, or reject with a reason; "
        "release a slot when the allocation ends; a floor-wise occupancy dashboard.",
        "Vehicle number registration, floor-wise filtering and occupancy dashboard (the case's bonus features).",
    ],
    "rules": [
        ("A slot has at most one active allocation", "`409`"),
        ("An employee has at most one active slot, and one pending request at a time", "`409`"),
        ("Only AVAILABLE slots can be allocated (reserved and closed slots cannot)", "`400`"),
        ("A slot that is in use cannot be marked reserved or closed until it is released", "`400`"),
        ("A vehicle number is tidied up (`mh 12-ab 1234` becomes `MH12AB1234`), must be 6 to 12 letters or digits and cannot be parked in two slots", "`400` field error / `409`"),
        ("Slot codes are unique (case and spaces ignored)", "`400` field error"),
        ("A decided request cannot be decided again; only a pending request can be cancelled, and only by its owner", "`409` / `403`"),
        ("Rejecting needs a reason, which the employee sees", "`400` field error"),
        ("Only admins manage slots and decide; only employees request", "`403`"),
    ],
    "concurrency": "approving locks the slot row, then the employee row, and only then loads the request (under `READ_COMMITTED`), so two admins "
                   "cannot give out the same slot, one request cannot be approved into two slots, and a double-clicked request creates one request. "
                   "The tests check all three with parallel threads.",
    "notes": [
        "Employees only see which slots are free, never who holds the others. The admin map shows occupant and vehicle on hover.",
        "Whether a slot is occupied is derived from the allocations table; the slot's own status is only AVAILABLE, RESERVED or MAINTENANCE.",
    ],
    "api": [
        ("GET", "/api/slots?floor=", "any", "Free slots (available and not held)"),
        ("GET", "/api/slots/floors", "any", "Floors that have slots"),
        ("POST", "/api/parking/requests", "EMPLOYEE", "Ask `{vehicleNumber, preferredFloor?, note?}`"),
        ("GET", "/api/parking/requests/my", "EMPLOYEE", "Own requests with queue position"),
        ("DELETE", "/api/parking/requests/{id}", "EMPLOYEE", "Cancel a pending request"),
        ("GET", "/api/parking/allocation/my", "EMPLOYEE", "Current slot (`204` when none)"),
        ("GET", "/api/admin/slots?floor=", "PARKING_ADMIN", "All slots with occupants"),
        ("POST", "/api/admin/slots", "PARKING_ADMIN", "Create a slot `{code, floor, status?}`"),
        ("PUT", "/api/admin/slots/{id}", "PARKING_ADMIN", "Edit a slot or change its status"),
        ("GET", "/api/admin/requests?status=", "PARKING_ADMIN", "Requests (pending = queue)"),
        ("PUT", "/api/admin/requests/{id}/approve", "PARKING_ADMIN", "Allocate `{slotId}`"),
        ("PUT", "/api/admin/requests/{id}/reject", "PARKING_ADMIN", "Reject `{reason}`"),
        ("GET", "/api/admin/allocations?activeOnly=", "PARKING_ADMIN", "Allocations (current or all)"),
        ("PUT", "/api/admin/allocations/{id}/release", "PARKING_ADMIN", "End an allocation"),
        ("GET", "/api/admin/dashboard", "PARKING_ADMIN", "Occupancy totals and per floor"),
    ],
    "controllers": "AuthController, ParkingController (employee), AdminController",
    "services": "ParkingService (slots, requests, allocation, locking, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository (row lock), SlotRepository (row lock), ParkingRequestRepository, ParkingAllocationRepository",
    "entities": "User, ParkingSlot, ParkingRequest, ParkingAllocation, Role, SlotStatus, RequestStatus",
    "tables": "`users`, `parking_slots` (unique code), `parking_requests` (employee, vehicle, status, decision, slot) and `parking_allocations` (slot, employee, vehicle, released-at).",
    "tests": "24 tests on in-memory H2 (no MySQL needed): vehicle numbers, queue, allocation rules, release, slot rules, dashboard, roles, parallel approvals",
    "collection": "Parking.postman_collection.json",
    "postman": "54 requests (84 assertions). It registers fresh employees and creates uniquely coded slots on floor 7 each run, so it can be run many times",
    "limits": [
        "One vehicle per employee; changing slots means releasing first.",
        "No time-limited or hourly bookings, only long-term allocations.",
        "Filtering and the queue are computed in memory (Java Streams), which is fine for one office car park.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
