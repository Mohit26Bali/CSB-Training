"""python tools/readmes/leave.py leave-portal/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Leave Request and Approval Portal",
    "summary": "Employees apply for leave and see their balance; managers approve or reject requests. "
               "Approved leave updates the balance. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8112, "frontend_port": 5112, "db": "leave_portal", "package": "leave",
    "logins": [("`manager`", "`manager123`", "MANAGER"), ("`neha.kapoor`", "`manager123`", "MANAGER"),
               ("`aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (10 employees)", "`employee123`", "EMPLOYEE")],
    "features": [
        "**Employee:** see the yearly balance for annual, sick and casual leave (and unpaid, which has no limit), including days "
        "already requested; request leave with a live working-day count; see all requests and their status; cancel a pending "
        "request, or approved leave that has not started yet.",
        "**Manager:** review queue with approve / reject (an optional message goes to the employee), search and filter by type, "
        "a team calendar showing who is away each day, and managers can take leave too (another manager decides it).",
        "Different leave types, calendar view and search/filter (the case's bonus features).",
    ],
    "rules": [
        ("End date cannot be before the start date", "`400` with a field error"),
        ("Leave days must be positive: Saturday and Sunday are not counted, so a weekend-only range is refused", "`400` with a field error"),
        ("Leave must start and end in the same calendar year", "`400` with a field error"),
        ("Not enough balance (pending requests count against it too)", "`400`"),
        ("Overlapping pending or approved leave for the same employee is prevented, at request time and again at approval", "`409`"),
        ("Approving deducts the days from the balance; cancelling approved future leave returns them", "balance stays correct"),
        ("An employee cannot approve or reject their own request (managers' own requests need another manager)", "`403`"),
        ("Only managers can decide requests; a decided request cannot be decided again", "`403` / `409`"),
        ("Leave that has already started cannot be cancelled", "`400`"),
    ],
    "concurrency": "every request, approval and cancellation for one employee first locks that employee's balance rows (then the "
                   "request row), under `READ_COMMITTED`. Two managers deciding the same request, or five overlapping requests "
                   "submitted at once, cannot both succeed. The tests check this with parallel threads.",
    "notes": ["Working days are Monday to Friday; public holidays are not modelled."],
    "api": [
        ("GET", "/api/leave/types", "any", "Leave types"),
        ("GET", "/api/leave/balance?year=", "any", "Own balance per type (this year, or last/next year)"),
        ("POST", "/api/leave", "any", "Request leave `{leaveType, startDate, endDate, reason}`"),
        ("GET", "/api/leave/my?status=&type=", "any", "Own requests"),
        ("DELETE", "/api/leave/{id}", "any", "Cancel own pending, or approved-and-not-started, leave"),
        ("GET", "/api/manager/leave?status=&type=&search=", "MANAGER", "All requests"),
        ("PUT", "/api/manager/leave/{id}/approve", "MANAGER", "Approve, optional `{note}`"),
        ("PUT", "/api/manager/leave/{id}/reject", "MANAGER", "Reject, optional `{note}`"),
        ("GET", "/api/manager/leave/calendar?month=2026-10", "MANAGER", "Approved leave in a month"),
    ],
    "controllers": "AuthController, LeaveController (everyone), ManagerLeaveController",
    "services": "LeaveService (all rules), AuthService, LoginAttemptService",
    "repos": "UserRepository, LeaveRequestRepository, LeaveBalanceRepository (locking queries)",
    "entities": "User, LeaveRequest, LeaveBalance, LeaveType, LeaveStatus, Role",
    "tables": "`users`, `leave_requests` (employee FK, type, dates, working days, status, decision fields) and `leave_balances` "
              "(unique per user, year and type).",
    "tests": "36 tests on in-memory H2 (no MySQL needed): working days, validation, overlap, balances, approval, cancellation, calendar, roles, concurrency",
    "collection": "LeavePortal.postman_collection.json",
    "postman": "38 requests (63 assertions). It uses next year's dates and registers a fresh employee, so it never clashes with the seed data",
    "limits": [
        "Public holidays and half-days are not modelled.",
        "Leave cannot span two calendar years: submit two requests.",
        "Filtering is done in memory (Java Streams), which is fine for a team but would need paging for a large company.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
