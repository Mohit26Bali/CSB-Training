"""python tools/readmes/warranty.py warranty/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Product Warranty Claim System",
    "summary": "Customers register what they have bought and raise warranty claims; service agents review the claims, approve or "
               "reject them, and move approved claims through repair to completion. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8107, "frontend_port": 5107, "db": "warranty_claims", "package": "warranty",
    "logins": [("`agent`", "`agent123`", "SERVICE_AGENT"), ("`sunil.agarwal`", "`agent123`", "SERVICE_AGENT"),
               ("`aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (10 customers)", "`customer123`", "CUSTOMER")],
    "features": [
        "**Customer:** register a purchased product (brand, serial number, purchase date, warranty months) and see how long the warranty "
        "lasts (covered, expiring within 30 days, or expired); file a claim with an issue description and a priority; follow every claim "
        "through PENDING → APPROVED → IN_REPAIR → COMPLETED (or REJECTED, with the reason) and read the resolution note.",
        "**Service agent:** a claims queue where pending claims are ordered high priority first, then longest waiting; approve, reject "
        "(reason required), start repair, and complete with an optional resolution note; search by product, serial number or customer; "
        "filter by status and priority; status counts on the tabs.",
        "Warranty expiry calculation, claim priority and product search (the case's bonus features), and a photo per product category.",
    ],
    "rules": [
        ("Only products whose warranty is still active (the last covered day included) can generate a claim", "`400`, the message gives the expiry date"),
        ("A product cannot have two active claims (PENDING, APPROVED or IN_REPAIR); completed or rejected claims do not block a new one", "`409`"),
        ("Rejection requires a reason", "`400` with a field error"),
        ("Serial numbers are unique (case-insensitive) across all customers", "`400` with a field error"),
        ("Purchase date cannot be in the future or more than 10 years ago; warranty is 1 to 60 months", "`400` with a field error"),
        ("Claim steps follow the workflow: approve or reject only PENDING, repair only APPROVED, complete only IN_REPAIR", "`409`"),
        ("Customers can claim only on their own products (someone else's product looks like \"not found\")", "`404`"),
        ("Only agents decide, and only customers register products and file claims", "`403` otherwise"),
    ],
    "concurrency": "filing a claim first locks the product row (under `READ_COMMITTED`), so a double click, or two devices, cannot create two "
                   "active claims for one product; every agent action locks the claim row, so two agents cannot decide the same claim. "
                   "The tests check both with parallel threads.",
    "notes": [
        "**Warranty end** = purchase date + warranty months, and that day is still covered. A product is \"expiring soon\" when 30 days or fewer remain.",
    ],
    "api": [
        ("GET", "/api/products/categories", "any", "Product categories"),
        ("POST", "/api/products", "CUSTOMER", "Register `{name, brand, category, serialNumber, purchaseDate, warrantyMonths}`"),
        ("GET", "/api/products/my?search=", "CUSTOMER", "Own products with warranty status"),
        ("POST", "/api/claims", "CUSTOMER", "File `{productId, issueDescription, priority?}`"),
        ("GET", "/api/claims/my?status=", "CUSTOMER", "Own claims"),
        ("GET", "/api/agent/claims?status=&priority=&search=", "SERVICE_AGENT", "The claims queue"),
        ("GET", "/api/agent/summary", "SERVICE_AGENT", "Count per status"),
        ("PUT", "/api/agent/claims/{id}/approve", "SERVICE_AGENT", "Approve a pending claim"),
        ("PUT", "/api/agent/claims/{id}/reject", "SERVICE_AGENT", "Reject with `{reason}`"),
        ("PUT", "/api/agent/claims/{id}/start-repair", "SERVICE_AGENT", "APPROVED → IN_REPAIR"),
        ("PUT", "/api/agent/claims/{id}/complete", "SERVICE_AGENT", "IN_REPAIR → COMPLETED, optional `{resolutionNote}`"),
    ],
    "controllers": "AuthController, CustomerController (products and claims), AgentController",
    "services": "ProductService, ClaimService (all claim rules), AuthService, LoginAttemptService",
    "repos": "UserRepository, ProductRepository (row lock), ClaimRepository (row lock)",
    "entities": "User, Product, Claim, Role, ClaimStatus, ClaimPriority, ProductCategory",
    "tables": "`users`, `products` (owner FK, **UNIQUE serial number**, purchase date, warranty months) and `claims` (product and customer FKs, status, priority, decision / repair / completion fields).",
    "tests": "29 tests on in-memory H2 (no MySQL needed): warranty maths, expiry, duplicate claims, ownership, workflow steps, queue order, roles, concurrency",
    "collection": "Warranty.postman_collection.json",
    "postman": "49 requests (80 assertions). It registers a fresh customer and uses unique serial numbers, so it can be run many times",
    "images_extra": " and the product pictures in `frontend/public/products` (`images-products.json`)",
    "limits": [
        "A customer cannot withdraw a claim after filing it, and cannot edit or remove a registered product.",
        "Warranty is a simple period from the purchase date; extended warranties and proof of purchase uploads are not modelled.",
        "Filtering is done in memory (Java Streams), which is fine for one service centre but would need paging at scale.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
