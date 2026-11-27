"""python tools/readmes/maintenance.py maintenance/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Community Maintenance Request System",
    "summary": "Residents report maintenance problems and follow them to completion; facility managers assign technicians, move requests "
               "through the work and record what was done. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8110, "frontend_port": 5110, "db": "community_maintenance", "package": "maintenance",
    "logins": [("`facility`", "`manager123`", "FACILITY_MANAGER"), ("`ravi.menon`", "`manager123`", "FACILITY_MANAGER"),
               ("`aarav.sharma` (a request in every state)", "`resident123`", "RESIDENT"),
               ("`diya.patel`, `rohan.mehta`, … (30 residents)", "`resident123`", "RESIDENT")],
    "features": [
        "**Resident:** report a problem (category, description, location, priority); see own requests with a five-step journey "
        "(reported, assigned, in progress, resolved, closed), who is assigned, how long it has waited and, once done, what was done.",
        "**Facility manager:** a queue ordered by urgency (emergencies first, then the longest waiting) with filters for status, category, "
        "priority and age, and search by place, problem, reference (MR-3042) or resident; assign or reassign a technician (those of the right "
        "trade and with the fewest jobs listed first); start work, resolve with notes, close; a technician directory (add, make unavailable); "
        "a dashboard with where requests are, ageing counts, requests per category and each technician's workload.",
        "Priority, category dashboard, search and filter, and an aging indicator (the case's bonus features).",
    ],
    "rules": [
        ("Category, description, location and priority are mandatory; unknown categories and priorities are refused", "`400` with a field error each"),
        ("Only facility managers assign or change status; only residents report, and residents see only their own requests", "`403`"),
        ("The flow is OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED. ASSIGNED comes only from assigning a technician; a resolved request can go back to IN_PROGRESS; closed is final", "`409` / `400`"),
        ("Resolution notes are mandatory to resolve; the resident sees them", "`400` field error"),
        ("Only active technicians can be assigned; a technician with active jobs cannot be made unavailable; technician names are unique", "`400`"),
        ("Aging of an active request: under 2 days is fresh, 2 to 4 days ageing, 5 or more stale", "computed"),
    ],
    "concurrency": "every assignment and status change locks the request row (under `READ_COMMITTED`), so when several managers click Start work "
                   "on the same assigned request, or two resolve it, exactly one wins and the others get a clear `409`. The tests check both with parallel threads.",
    "notes": [
        "The reference (MR-3042) is the request id plus 3000. Residents' requests are never edited after they are sent; the manager's notes carry the story.",
        "The technician list is separate from user accounts: technicians can be staff or outside vendors and do not log in.",
    ],
    "api": [
        ("POST", "/api/requests", "RESIDENT", "Report `{category, description, location, priority}`"),
        ("GET", "/api/requests/my?status=&search=", "RESIDENT", "Own requests"),
        ("GET", "/api/requests/meta", "any", "Categories and priorities"),
        ("GET", "/api/requests/{id}", "owner / managers", "One request"),
        ("GET", "/api/manager/requests?status=&priority=&category=&technicianId=&aging=&search=", "FACILITY_MANAGER", "The queue"),
        ("PUT", "/api/manager/requests/{id}/assign", "FACILITY_MANAGER", "Assign `{technicianId}` (moves OPEN to ASSIGNED)"),
        ("PUT", "/api/manager/requests/{id}/status", "FACILITY_MANAGER", "Move `{status, notes?}` (notes mandatory for RESOLVED)"),
        ("GET", "/api/manager/technicians", "FACILITY_MANAGER", "Technicians with their active jobs"),
        ("POST", "/api/manager/technicians", "FACILITY_MANAGER", "Add `{name, trade, phone?}`"),
        ("PUT", "/api/manager/technicians/{id}/active?value=", "FACILITY_MANAGER", "Make available or unavailable"),
        ("GET", "/api/manager/dashboard", "FACILITY_MANAGER", "Statuses, ageing, categories, technician load"),
    ],
    "controllers": "AuthController, RequestController (everyone), ManagerController",
    "services": "MaintenanceService (requests, queue, assignment, status flow, technicians, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository, MaintenanceRequestRepository (row lock), TechnicianRepository",
    "entities": "User, MaintenanceRequest, Technician, Role, Category, Priority, RequestStatus (knows its legal next steps)",
    "tables": "`users`, `technicians` (unique name, trade, active) and `maintenance_requests` (resident and technician FKs, status, location, resolution notes).",
    "tests": "25 tests on in-memory H2 (no MySQL needed): reporting, visibility, assignment, status flow, ageing, queue order, technicians, dashboard, roles, parallel updates",
    "collection": "Maintenance.postman_collection.json",
    "postman": "53 requests (83 assertions). It registers a fresh resident and uses a uniquely named location and technician each run, so it can be run many times",
    "limits": [
        "No photos of the problem, no scheduled visits and no notifications.",
        "Filtering, search and the queue order are done in memory (Java Streams), which is fine for one community but would need paging at scale.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
