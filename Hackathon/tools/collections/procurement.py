"""python tools/collections/procurement.py procurement/docs/postman/Procurement.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
pm.collectionVariables.set("runId", String(Math.floor(Math.random() * 900000 + 100000)));
pm.collectionVariables.set("newUser", "postman." + pm.collectionVariables.get("runId"));
"""


def item(name="Postman Monitor {{runId}}", category="HARDWARE", qty=2, cost=50, why="Dual screens"):
    return {"itemName": name, "category": category, "quantity": qty, "estimatedUnitCost": cost, "justification": why}


items = [
    folder("1. Auth", [
        req("Login as procurement manager", "POST", "/api/auth/login", body={"username": "procurement", "password": "manager123"},
            tests=login_tests("managerToken"), pre=PRE),
        req("Login as second manager", "POST", "/api/auth/login", body={"username": "kiran.shah", "password": "manager123"},
            tests=login_tests("manager2Token")),
        req("Register a fresh employee for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Tester", "role": "PROCUREMENT_MANAGER"}, tests=f"""
{status(201)}
pm.test("Always an employee", () => pm.expect(pm.response.json().role).to.eql("EMPLOYEE"));
pm.collectionVariables.set("employeeToken", pm.response.json().token);
"""),
        req("Login as a seeded employee", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "employee123"},
            tests=login_tests("employee2Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "procurement", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Submit a request", [
        req("Categories", "GET", "/api/requests/categories", token="employeeToken", tests=f"""
{status(200)}
pm.test("Has labelled categories", () => pm.expect(pm.response.json()[0]).to.have.keys("value", "label"));
"""),
        req("Valid request (2 x 50 = 100)", "POST", "/api/requests", token="employeeToken", body=item(), tests=f"""
{status(201)}
const r = pm.response.json();
pm.test("Pending, with the total worked out", () => {{ pm.expect(r.status).to.eql("PENDING"); pm.expect(r.estimatedTotal).to.eql(100); }});
pm.collectionVariables.set("requestId", r.id);
"""),
        req("Zero quantity is rejected", "POST", "/api/requests", token="employeeToken", body=item(qty=0), tests=f"""
{status(400)}
pm.test("Quantity field error", () => pm.expect(pm.response.json().fieldErrors.quantity).to.include("at least 1"));
"""),
        req("Negative cost is rejected", "POST", "/api/requests", token="employeeToken", body=item(cost=-5), tests=f"""
{status(400)}
pm.test("Cost field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("estimatedUnitCost"));
"""),
        req("Missing fields list every problem", "POST", "/api/requests", token="employeeToken",
            body={"itemName": "", "category": "", "quantity": 0, "estimatedUnitCost": 0, "justification": ""}, tests=f"""
{status(400)}
pm.test("All bad fields are listed", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("itemName", "category", "quantity", "estimatedUnitCost", "justification"));
"""),
        req("Unknown category is rejected", "POST", "/api/requests", token="employeeToken", body=item(category="YACHTS"), tests=f"""
{status(400)}
pm.test("Category field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("category"));
"""),
    ]),
    folder("3. Employee views", [
        req("My requests", "GET", "/api/requests/my", token="employeeToken", tests=f"""
{status(200)}
pm.test("Exactly my one request", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Filters (status, category, search)", "GET", "/api/requests/my?status=PENDING&category=hardware&search=monitor", token="employeeToken", tests=f"""
{status(200)}
pm.test("Still finds it", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Summary has all five statuses", "GET", "/api/requests/my/summary", token="employeeToken", tests=f"""
{status(200)}
pm.test("Five rows, one pending", () => {{
  const s = pm.response.json();
  pm.expect(s.length).to.eql(5);
  pm.expect(s.find(x => x.status === "PENDING").count).to.eql(1);
}});
"""),
        req("Another employee does not see it", "GET", "/api/requests/my?search={{runId}}", token="employee2Token", tests=f"""
{status(200)}
pm.test("Nothing", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
    ]),
    folder("4. Approve, order, deliver", [
        req("Pending list includes the request", "GET", "/api/manager/requests?status=PENDING&search={{runId}}", token="managerToken", tests=f"""
{status(200)}
pm.test("Found", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Ordering before approval is a conflict", "PUT", "/api/manager/requests/{{requestId}}/order", token="managerToken", tests=f"""
{status(409)}
pm.test("Only approved requests can be ordered", () => pm.expect(pm.response.json().message).to.include("Only approved"));
"""),
        req("Delivering before ordering is a conflict", "PUT", "/api/manager/requests/{{requestId}}/deliver", token="managerToken", tests=status(409)),
        req("Approve", "PUT", "/api/manager/requests/{{requestId}}/approve", token="managerToken", tests=f"""
{status(200)}
pm.test("Approved and recorded", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("APPROVED"); pm.expect(r.decidedBy).to.eql("procurement"); }});
"""),
        req("Approve again is a conflict", "PUT", "/api/manager/requests/{{requestId}}/approve", token="manager2Token", tests=status(409)),
        req("Mark as ordered with a PO reference", "PUT", "/api/manager/requests/{{requestId}}/order", token="managerToken",
            body={"orderReference": "PO-{{runId}}"}, tests=f"""
{status(200)}
pm.test("Ordered with reference", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("ORDERED"); pm.expect(r.orderReference).to.include("PO-"); }});
"""),
        req("Mark as delivered", "PUT", "/api/manager/requests/{{requestId}}/deliver", token="managerToken", tests=f"""
{status(200)}
pm.test("Delivered", () => pm.expect(pm.response.json().status).to.eql("DELIVERED"));
"""),
        req("Delivering twice is a conflict", "PUT", "/api/manager/requests/{{requestId}}/deliver", token="managerToken", tests=status(409)),
        req("Employee sees the whole trail", "GET", "/api/requests/my?status=DELIVERED", token="employeeToken", tests=f"""
{status(200)}
pm.test("Has approval, order and delivery details", () => {{
  const r = pm.response.json()[0];
  pm.expect(r.decidedBy).to.eql("procurement"); pm.expect(r.orderedAt).to.be.a("string"); pm.expect(r.deliveredAt).to.be.a("string");
}});
"""),
    ]),
    folder("5. Rejection", [
        req("Submit a second request", "POST", "/api/requests", token="employeeToken", body=item(name="Postman Chair {{runId}}", category="FURNITURE"), tests=f"""
{status(201)}
pm.collectionVariables.set("request2Id", pm.response.json().id);
"""),
        req("Reject without a reason is 400", "PUT", "/api/manager/requests/{{request2Id}}/reject", token="managerToken", body={"reason": ""}, tests=f"""
{status(400)}
pm.test("Reason field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("reason"));
"""),
        req("Reject with a reason", "PUT", "/api/manager/requests/{{request2Id}}/reject", token="managerToken", body={"reason": "Please get two quotes first"}, tests=f"""
{status(200)}
pm.test("Rejected with the reason", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("REJECTED"); pm.expect(r.rejectionReason).to.include("two quotes"); }});
"""),
        req("A rejected request cannot be ordered", "PUT", "/api/manager/requests/{{request2Id}}/order", token="managerToken", tests=status(409)),
        req("Employee reads the reason", "GET", "/api/requests/my?status=REJECTED", token="employeeToken", tests=f"""
{status(200)}
pm.test("Reason visible", () => pm.expect(pm.response.json()[0].rejectionReason).to.include("two quotes"));
"""),
    ]),
    folder("6. Monthly budget", [
        req("Submit a request far above the budget", "POST", "/api/requests", token="employeeToken",
            body=item(name="Postman Datacentre {{runId}}", cost=10000000, qty=1, why="Budget check"), tests=f"""
{status(201)}
pm.collectionVariables.set("hugeId", pm.response.json().id);
"""),
        req("Approving it would exceed the budget", "PUT", "/api/manager/requests/{{hugeId}}/approve", token="managerToken", tests=f"""
{status(409)}
pm.test("Explains the budget", () => pm.expect(pm.response.json().message).to.include("exceed"));
"""),
        req("It is still pending", "GET", "/api/manager/requests?status=PENDING&search=Datacentre {{runId}}", token="managerToken", tests=f"""
{status(200)}
pm.test("Not approved", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Reject it to tidy up", "PUT", "/api/manager/requests/{{hugeId}}/reject", token="managerToken", body={"reason": "Over the monthly budget"}, tests=status(200)),
        req("Dashboard", "GET", "/api/manager/dashboard", token="managerToken", tests=f"""
{status(200)}
pm.test("Budget figures add up", () => {{
  const d = pm.response.json();
  pm.expect(d.byStatus.length).to.eql(5);
  pm.expect(d.spentThisMonth + d.remainingThisMonth).to.eql(d.monthlyBudget);
  pm.expect(d.spendByCategory).to.be.an("array");
}});
"""),
    ]),
    folder("7. Security checks", [
        req("No token is 401", "GET", "/api/requests/my", tests=status(401)),
        req("Employee cannot list all requests", "GET", "/api/manager/requests", token="employeeToken", tests=status(403)),
        req("Employee cannot approve", "PUT", "/api/manager/requests/{{request2Id}}/approve", token="employeeToken", tests=status(403)),
        req("Employee cannot order or deliver", "PUT", "/api/manager/requests/{{requestId}}/order", token="employeeToken", tests=status(403)),
        req("Employee cannot open the dashboard", "GET", "/api/manager/dashboard", token="employeeToken", tests=status(403)),
        req("Manager cannot submit requests", "POST", "/api/requests", token="managerToken", body=item(), tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Procurement Portal API",
    "Walks the procurement workflow: submit, validation, approve, order, deliver, rejection, the monthly budget and security.\n"
    "Start the backend first (http://localhost:8119). Repeatable: it registers a fresh employee and uses tiny amounts.",
    items, {"baseUrl": "http://localhost:8119", **{k: "" for k in [
        "managerToken", "manager2Token", "employeeToken", "employee2Token", "requestId", "request2Id", "hugeId", "runId", "newUser"]}})
