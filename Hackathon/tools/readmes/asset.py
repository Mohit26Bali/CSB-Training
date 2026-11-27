"""python tools/readmes/asset.py asset-allocation/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Asset Allocation System",
    "summary": "IT keeps a register of laptops, monitors, phones and other equipment, hands each item to one employee at a time, and checks it back in when the employee "
               "raises a return request. Every asset keeps its full assignment history. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8115, "frontend_port": 5115, "db": "asset_allocation", "package": "asset",
    "logins": [("`assetadmin`", "`admin123`", "ASSET_ADMIN"), ("`it.desk`", "`admin123`", "ASSET_ADMIN"),
               ("`aarav.sharma` (holds a MacBook Pro and a monitor, has asked to return a headset, once returned a Dell laptop)", "`employee123`", "EMPLOYEE"),
               ("`diya.patel`, `rohan.mehta`, … (30 employees)", "`employee123`", "EMPLOYEE")],
    "features": [
        "**Asset admin:** a register of assets (tag, serial number, category, brand, model, purchase date and price, condition), search by serial number, tag, name or holder with status and category filters, "
        "assign an available asset to an employee (optional due date), a return queue with check-in and the condition it came back in, the full assignment history of every asset, "
        "sending assets to repair and retiring them, and a dashboard (counts, overdue, poor condition, value, breakdowns, oldest requests).",
        "**Employee:** the equipment they hold with due dates, a return request with a note, and a history of what they handed back.",
        "Asset search by serial number and condition tracking (the case's bonus features).",
    ],
    "rules": [
        ("Only AVAILABLE assets can be assigned; an asset cannot be held by two employees (the message says who has it)", "`409`"),
        ("An accepted return makes the asset AVAILABLE again, with the condition recorded at check-in", "status change"),
        ("The asset tag (like LT-1001) and the serial number are unique, whatever the letter case", "`400` field errors"),
        ("Assets go only to active employees; one employee holds at most 5 assets; the due date must be after today", "`400`"),
        ("Only the holder raises a return request, once; only an admin accepts it, and only after it was raised", "`403` / `409`"),
        ("AVAILABLE and IN_REPAIR can be swapped; either can be retired for good; held assets must come back first", "`400` / `409`"),
        ("A retired asset is read-only; the purchase date cannot be in the future; the price cannot be negative", "`400`"),
    ],
    "concurrency": "assigning, requesting a return, accepting it and every status change take a row lock on the asset (under `READ_COMMITTED`, and before the assignment is loaded), "
                   "assigning also locks the employee, so five admins handing one laptop to five people make one assignment, seven assets assigned to one employee at once stop at five, "
                   "and a double-clicked return request or a double-clicked check-in is recorded once. The tests check this with parallel threads.",
    "notes": [
        "Photos are looked up by category (`/assets/<category>.jpg`); without one a line icon is shown on a stone tile.",
        "Accepting a return always makes the asset available, even in POOR condition; the admin can then send it to repair.",
    ],
    "api": [
        ("GET", "/api/assets/meta", "any signed-in user", "Category and condition values for forms"),
        ("GET", "/api/my/assets", "EMPLOYEE", "Equipment held now"),
        ("GET", "/api/my/history", "EMPLOYEE", "Equipment handed back"),
        ("POST", "/api/my/assignments/{id}/return", "EMPLOYEE", "Raise a return request `{note?}`"),
        ("GET", "/api/admin/assets?status=&category=&search=", "ASSET_ADMIN", "The register"),
        ("POST", "/api/admin/assets", "ASSET_ADMIN", "Register an asset"),
        ("PUT", "/api/admin/assets/{id}", "ASSET_ADMIN", "Edit an asset"),
        ("PUT", "/api/admin/assets/{id}/status", "ASSET_ADMIN", "Repair, repaired or retire `{status}`"),
        ("POST", "/api/admin/assets/{id}/assign", "ASSET_ADMIN", "Assign `{employeeUsername, dueDate?, note?}`"),
        ("GET", "/api/admin/assets/{id}/history", "ASSET_ADMIN", "Every assignment of the asset"),
        ("GET", "/api/admin/assignments?status=", "ASSET_ADMIN", "Return requests (default) or assets out with staff"),
        ("PUT", "/api/admin/assignments/{id}/accept", "ASSET_ADMIN", "Check in `{condition, note?}`"),
        ("GET", "/api/admin/employees?search=", "ASSET_ADMIN", "Employees with the number of assets held"),
        ("GET", "/api/admin/dashboard", "ASSET_ADMIN", "Counts, value, breakdowns, oldest requests"),
    ],
    "controllers": "AuthController, EmployeeController, AdminController",
    "services": "AssetService (register, assigning, returns, history, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository (row lock), AssetRepository (row lock), AssignmentRepository",
    "entities": "User, Asset, Assignment, Role, AssetCategory, AssetCondition, AssetStatus, AssignmentStatus",
    "tables": "`users`, `assets` (unique tag and serial, condition, status) and `assignments` (asset and employee FKs, due date, condition out and in, return request, who handed over and who received).",
    "tests": "29 tests on in-memory H2 (no MySQL needed): register rules, search, assigning, the five-asset limit, return flow, history, repair and retirement, dashboard, roles, parallel assignments and returns",
    "collection": "Asset.postman_collection.json",
    "postman": "62 requests (99 assertions). It registers a fresh employee and assets with a unique tag and serial each run, so it can be run many times",
    "limits": [
        "No bulk import, barcode scanning or depreciation.",
        "The register is filtered in memory (Java Streams), which suits a company of this size but would need paging for tens of thousands of assets.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
