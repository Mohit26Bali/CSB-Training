"""python tools/readmes/procurement.py procurement/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Simple Procurement Request System",
    "summary": "Employees request office items and software; procurement managers approve or reject within a monthly budget, "
               "then mark approved requests as ordered and delivered. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8119, "frontend_port": 5119, "db": "procurement_requests", "package": "procurement",
    "logins": [("`procurement`", "`manager123`", "PROCUREMENT_MANAGER"), ("`kiran.shah`", "`manager123`", "PROCUREMENT_MANAGER"),
               ("`aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (12 employees)", "`employee123`", "EMPLOYEE")],
    "features": [
        "**Employee:** create a request (item, category, quantity, estimated cost per unit, justification) with a live total; "
        "track only their own requests through PENDING → APPROVED → ORDERED → DELIVERED (or REJECTED, with the reason); "
        "filter by status and category and search; a summary of counts and totals per status.",
        "**Procurement manager:** review queue; approve, reject (reason required), mark as ordered (with an optional purchase-order "
        "reference) and mark as delivered; a dashboard with the month's budget, spend by category and the oldest pending request.",
        "Budget limit validation, category filtering and a spending dashboard (the case's bonus features).",
    ],
    "rules": [
        ("Quantity must be a positive whole number (at most 10,000)", "`400` with a field error"),
        ("Estimated cost must be greater than zero (at most 2 decimal places)", "`400` with a field error"),
        ("Only procurement managers can approve, reject, order or deliver", "`403` for employees"),
        ("Rejection requires a reason", "`400` with a field error"),
        ("A rejected (or still pending) request cannot be ordered; delivery needs ORDERED; no step can be repeated", "`409`"),
        ("Approval is refused if it would take this month's approved spend over the monthly budget", "`409`, the message names the amount left"),
        ("A manager cannot decide their own request", "`403`"),
        ("Employees see only their own requests", "list is filtered by the logged-in user"),
    ],
    "concurrency": "approving first locks a single-row \"budget gate\" and then the request row, under `READ_COMMITTED`, so two approvals "
                   "can never both fit under the same remaining budget (the tests race five 30,000 approvals against a 100,000 budget "
                   "and exactly three win), and two managers cannot decide the same request.",
    "notes": [
        "The cost entered is **per unit**; the total is quantity × unit cost. Spend counts in the month a request was approved. "
        "The monthly limit defaults to ₹500,000 and is set with the `MONTHLY_BUDGET` environment variable.",
    ],
    "api": [
        ("GET", "/api/requests/categories", "any", "Categories"),
        ("POST", "/api/requests", "EMPLOYEE", "Create `{itemName, category, quantity, estimatedUnitCost, justification}`"),
        ("GET", "/api/requests/my?status=&category=&search=", "EMPLOYEE", "Own requests, newest first"),
        ("GET", "/api/requests/my/summary", "EMPLOYEE", "Count and total per status"),
        ("GET", "/api/manager/requests?status=&category=&search=", "PROCUREMENT_MANAGER", "All requests"),
        ("PUT", "/api/manager/requests/{id}/approve", "PROCUREMENT_MANAGER", "Approve (budget checked)"),
        ("PUT", "/api/manager/requests/{id}/reject", "PROCUREMENT_MANAGER", "Reject with `{reason}`"),
        ("PUT", "/api/manager/requests/{id}/order", "PROCUREMENT_MANAGER", "Mark ordered, optional `{orderReference}`"),
        ("PUT", "/api/manager/requests/{id}/deliver", "PROCUREMENT_MANAGER", "Mark delivered"),
        ("GET", "/api/manager/dashboard", "PROCUREMENT_MANAGER", "Budget, status totals, spend by category"),
    ],
    "controllers": "AuthController, RequestController (employee), ManagerRequestController",
    "services": "ProcurementService (all rules), AuthService, LoginAttemptService",
    "repos": "UserRepository, PurchaseRequestRepository (row lock), BudgetGateRepository (budget lock)",
    "entities": "User, PurchaseRequest, BudgetGate, Role, RequestStatus, ItemCategory",
    "tables": "`users`, `purchase_requests` (employee FK, item, category, quantity, unit cost, status, decision / order / delivery fields) and `budget_gate`.",
    "tests": "24 tests on in-memory H2 (no MySQL needed): rules, visibility, roles, the order/deliver workflow, budget limit, concurrency",
    "collection": "Procurement.postman_collection.json",
    "postman": "40 requests (66 assertions). It registers a fresh employee and uses tiny amounts, so it can be run many times",
    "limits": [
        "One company-wide monthly budget; there are no per-department budgets.",
        "Filtering is done in memory (Java Streams), which is fine for one company's requests but would need paging at scale.",
        "Employees cannot edit or withdraw a request after submitting it.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
