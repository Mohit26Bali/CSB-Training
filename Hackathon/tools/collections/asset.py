"""python tools/collections/asset.py asset-allocation/docs/postman/Asset.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
const day = (offset) => { const d = new Date(Date.now() + offset * 864e5); return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0"); };
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("tagA", "PM-" + id);
pm.collectionVariables.set("tagB", "PX-" + id);
pm.collectionVariables.set("tagAlower", "pm-" + id);
pm.collectionVariables.set("serialA", "PMSNA" + id);
pm.collectionVariables.set("serialB", "PMSNB" + id);
pm.collectionVariables.set("serialAlower", "pmsna" + id);
pm.collectionVariables.set("tomorrow", day(1));
pm.collectionVariables.set("nextMonth", day(30));
pm.collectionVariables.set("today", day(0));
"""


def asset(tag="{{tagA}}", serial="{{serialA}}", name="Postman Laptop", category="LAPTOP", condition="GOOD", **extra):
    body = {"assetTag": tag, "serialNumber": serial, "name": name, "category": category, "brand": "Dell", "model": "Latitude",
            "purchaseDate": "2025-01-15", "purchasePrice": 85000, "condition": condition}
    body.update(extra)
    return body


items = [
    folder("1. Auth", [
        req("Login as asset admin", "POST", "/api/auth/login", body={"username": "assetadmin", "password": "admin123"},
            tests=login_tests("adminToken"), pre=PRE),
        req("Register a fresh employee for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Employee", "role": "ASSET_ADMIN"}, tests=f"""
{status(201)}
pm.test("Always an employee", () => pm.expect(pm.response.json().role).to.eql("EMPLOYEE"));
pm.collectionVariables.set("employeeToken", pm.response.json().token);
"""),
        req("Login as another employee", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "employee123"},
            tests=login_tests("otherToken")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "assetadmin", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. The register", [
        req("Empty form lists every problem", "POST", "/api/admin/assets", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("assetTag", "serialNumber", "name", "category", "condition"));
"""),
        req("Bad formats are explained", "POST", "/api/admin/assets", token="adminToken", body=asset("BAD", "x", purchasePrice=-5), tests=f"""
{status(400)}
pm.test("Format hints", () => {{ const e = pm.response.json().fieldErrors; pm.expect(e.assetTag).to.include("LT-1001"); pm.expect(e.serialNumber).to.include("4 to 30"); pm.expect(e.purchasePrice).to.include("negative"); }});
"""),
        req("A purchase date in the future is rejected", "POST", "/api/admin/assets", token="adminToken", body=asset(purchaseDate="2999-01-01"), tests=f"""
{status(400)}
pm.test("Date error", () => pm.expect(pm.response.json().fieldErrors.purchaseDate).to.include("future"));
"""),
        req("Register a laptop", "POST", "/api/admin/assets", token="adminToken", body=asset(), tests=f"""
{status(201)}
const a = pm.response.json();
pm.test("Available, tag and serial upper-case", () => {{ pm.expect(a.status).to.eql("AVAILABLE"); pm.expect(a.assetTag).to.eql(pm.collectionVariables.get("tagA")); pm.expect(a.holderName).to.be.null; }});
pm.collectionVariables.set("assetA", a.id);
"""),
        req("Register a monitor", "POST", "/api/admin/assets", token="adminToken", body=asset("{{tagB}}", "{{serialB}}", "Postman Monitor", "MONITOR", "NEW"), tests=f"""
{status(201)}
pm.collectionVariables.set("assetB", pm.response.json().id);
"""),
        req("The tag cannot be reused in any case", "POST", "/api/admin/assets", token="adminToken", body=asset("{{tagAlower}}", "SOMETHING-ELSE-1"), tests=f"""
{status(400)}
pm.test("Tag error", () => pm.expect(pm.response.json().fieldErrors.assetTag).to.include("already in use"));
"""),
        req("The serial number cannot be reused in any case", "POST", "/api/admin/assets", token="adminToken", body=asset("PZ-{{runId}}", "{{serialAlower}}"), tests=f"""
{status(400)}
pm.test("Serial error", () => pm.expect(pm.response.json().fieldErrors.serialNumber).to.include("already registered"));
"""),
        req("Employees cannot register assets", "POST", "/api/admin/assets", token="employeeToken", body=asset("PQ-{{runId}}", "PQSN{{runId}}"), tests=status(403)),
        req("Edit the laptop", "PUT", "/api/admin/assets/{{assetA}}", token="adminToken", body=asset(name="Postman Laptop Pro", notes="Edited by the run"), tests=f"""
{status(200)}
pm.test("Saved", () => {{ pm.expect(pm.response.json().name).to.eql("Postman Laptop Pro"); pm.expect(pm.response.json().notes).to.eql("Edited by the run"); }});
"""),
    ]),
    folder("3. Search", [
        req("Find by serial number, any case", "GET", "/api/admin/assets?search={{serialAlower}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Exactly the laptop", () => {{ pm.expect(pm.response.json().length).to.eql(1); pm.expect(pm.response.json()[0].id).to.eql(Number(pm.collectionVariables.get("assetA"))); }});
"""),
        req("Find by tag without the dash", "GET", "/api/admin/assets?search=pm{{runId}}", token="adminToken", tests=f"""
{status(200)}
pm.test("One result", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Filter by category and status", "GET", "/api/admin/assets?category=MONITOR&status=AVAILABLE&search={{runId}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Just the monitor", () => {{ pm.expect(pm.response.json().length).to.eql(1); pm.expect(pm.response.json()[0].category).to.eql("MONITOR"); }});
"""),
        req("Nothing matches a made-up serial", "GET", "/api/admin/assets?search=zzzzzzzzzz", token="adminToken", tests=f"""
{status(200)}
pm.test("Empty", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("A bad status filter is rejected", "GET", "/api/admin/assets?status=BOGUS", token="adminToken", tests=status(400)),
        req("Categories and conditions for forms", "GET", "/api/assets/meta", token="employeeToken", tests=f"""
{status(200)}
pm.test("Both lists", () => {{ pm.expect(pm.response.json().categories).to.include("LAPTOP"); pm.expect(pm.response.json().conditions).to.eql(["NEW", "GOOD", "FAIR", "POOR"]); }});
"""),
    ]),
    folder("4. Assigning", [
        req("No employee is rejected", "POST", "/api/admin/assets/{{assetA}}/assign", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("Employee error", () => pm.expect(pm.response.json().fieldErrors.employeeUsername).to.eql("Choose an employee"));
"""),
        req("An unknown employee is rejected", "POST", "/api/admin/assets/{{assetA}}/assign", token="adminToken", body={"employeeUsername": "nobody.here"}, tests=status(400)),
        req("An admin cannot hold assets", "POST", "/api/admin/assets/{{assetA}}/assign", token="adminToken", body={"employeeUsername": "assetadmin"}, tests=f"""
{status(400)}
pm.test("Employees only", () => pm.expect(pm.response.json().fieldErrors.employeeUsername).to.include("employees"));
"""),
        req("A due date today is rejected", "POST", "/api/admin/assets/{{assetA}}/assign", token="adminToken", body={"employeeUsername": "{{newUser}}", "dueDate": "{{today}}"}, tests=f"""
{status(400)}
pm.test("Due date error", () => pm.expect(pm.response.json().fieldErrors.dueDate).to.include("after today"));
"""),
        req("Employees cannot assign", "POST", "/api/admin/assets/{{assetA}}/assign", token="employeeToken", body={"employeeUsername": "{{newUser}}"}, tests=status(403)),
        req("Assign the laptop to the employee", "POST", "/api/admin/assets/{{assetA}}/assign", token="adminToken",
            body={"employeeUsername": "{{newUser}}", "dueDate": "{{nextMonth}}", "note": "For the Postman project"}, tests=f"""
{status(201)}
const a = pm.response.json();
pm.test("Assigned, condition out recorded", () => {{ pm.expect(a.status).to.eql("ASSIGNED"); pm.expect(a.conditionOut).to.eql("GOOD"); pm.expect(a.assignedBy).to.eql("assetadmin"); pm.expect(a.canRequestReturn).to.be.true; pm.expect(a.overdue).to.be.false; }});
pm.collectionVariables.set("assignment1", a.id);
"""),
        req("It cannot be assigned twice", "POST", "/api/admin/assets/{{assetA}}/assign", token="adminToken", body={"employeeUsername": "diya.patel"}, tests=f"""
{status(409)}
pm.test("Says who has it", () => {{ pm.expect(pm.response.json().message).to.include("Only available assets"); pm.expect(pm.response.json().message).to.include("Postman Employee"); }});
"""),
        req("The register shows who holds it", "GET", "/api/admin/assets?search={{newUser}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Assigned with the holder", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].status).to.eql("ASSIGNED"); pm.expect(l[0].holderUsername).to.eql(pm.collectionVariables.get("newUser")); }});
"""),
        req("The employee sees it", "GET", "/api/my/assets", token="employeeToken", tests=f"""
{status(200)}
pm.test("One item with its due date", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].assetTag).to.eql(pm.collectionVariables.get("tagA")); pm.expect(l[0].dueDate).to.eql(pm.collectionVariables.get("nextMonth")); }});
"""),
        req("Another employee does not", "GET", "/api/my/assets", token="otherToken", tests=f"""
{status(200)}
pm.test("Not theirs", () => pm.expect(pm.response.json().some(a => a.assetTag === pm.collectionVariables.get("tagA"))).to.be.false);
"""),
        req("The admin has no personal equipment list", "GET", "/api/my/assets", token="adminToken", tests=status(403)),
    ]),
    folder("5. Returning", [
        req("Another employee cannot return it", "POST", "/api/my/assignments/{{assignment1}}/return", token="otherToken", tests=status(403)),
        req("The admin cannot accept before the request", "PUT", "/api/admin/assignments/{{assignment1}}/accept", token="adminToken", body={"condition": "GOOD"}, tests=f"""
{status(409)}
pm.test("Not asked yet", () => pm.expect(pm.response.json().message).to.include("not asked"));
"""),
        req("The employee asks to return it", "POST", "/api/my/assignments/{{assignment1}}/return", token="employeeToken", body={"note": "Project finished"}, tests=f"""
{status(200)}
pm.test("Return requested", () => {{ const a = pm.response.json(); pm.expect(a.status).to.eql("RETURN_REQUESTED"); pm.expect(a.returnNote).to.eql("Project finished"); pm.expect(a.canRequestReturn).to.be.false; }});
"""),
        req("Asking twice is a conflict", "POST", "/api/my/assignments/{{assignment1}}/return", token="employeeToken", tests=status(409)),
        req("The asset shows the request", "GET", "/api/admin/assets?search={{serialA}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Return requested", () => pm.expect(pm.response.json()[0].status).to.eql("RETURN_REQUESTED"));
"""),
        req("It is in the queue", "GET", "/api/admin/assignments", token="adminToken", tests=f"""
{status(200)}
pm.test("Queue first", () => {{ const l = pm.response.json(); const mine = l.find(a => a.id == pm.collectionVariables.get("assignment1")); pm.expect(mine.employeeUsername).to.eql(pm.collectionVariables.get("newUser")); pm.expect(l[0].status).to.eql("RETURN_REQUESTED"); }});
"""),
        req("A missing condition is rejected", "PUT", "/api/admin/assignments/{{assignment1}}/accept", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("Condition error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("condition"));
"""),
        req("Employees cannot accept", "PUT", "/api/admin/assignments/{{assignment1}}/accept", token="employeeToken", body={"condition": "GOOD"}, tests=status(403)),
        req("The admin checks it in as FAIR", "PUT", "/api/admin/assignments/{{assignment1}}/accept", token="adminToken", body={"condition": "FAIR", "note": "Scratch on the lid"}, tests=f"""
{status(200)}
pm.test("Returned and recorded", () => {{ const a = pm.response.json(); pm.expect(a.status).to.eql("RETURNED"); pm.expect(a.conditionIn).to.eql("FAIR"); pm.expect(a.receivedBy).to.eql("assetadmin"); }});
"""),
        req("The asset is available again, in FAIR condition", "GET", "/api/admin/assets?search={{serialA}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Available and FAIR", () => {{ pm.expect(pm.response.json()[0].status).to.eql("AVAILABLE"); pm.expect(pm.response.json()[0].condition).to.eql("FAIR"); }});
"""),
        req("Accepting twice is a conflict", "PUT", "/api/admin/assignments/{{assignment1}}/accept", token="adminToken", body={"condition": "GOOD"}, tests=status(409)),
        req("The employee's history shows it", "GET", "/api/my/history", token="employeeToken", tests=f"""
{status(200)}
pm.test("One returned item", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].conditionIn).to.eql("FAIR"); }});
"""),
        req("Returning it again is a conflict", "POST", "/api/my/assignments/{{assignment1}}/return", token="employeeToken", tests=status(409)),
        req("Hand it to someone else", "POST", "/api/admin/assets/{{assetA}}/assign", token="adminToken", body={"employeeUsername": "diya.patel"}, tests=f"""
{status(201)}
pm.test("Condition out is the returned condition", () => pm.expect(pm.response.json().conditionOut).to.eql("FAIR"));
"""),
        req("The asset's history keeps both holders", "GET", "/api/admin/assets/{{assetA}}/history", token="adminToken", tests=f"""
{status(200)}
pm.test("Newest first", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].employeeUsername).to.eql("diya.patel"); pm.expect(l[1].status).to.eql("RETURNED"); }});
"""),
    ]),
    folder("6. Repair and retirement", [
        req("Send the monitor to repair", "PUT", "/api/admin/assets/{{assetB}}/status", token="adminToken", body={"status": "IN_REPAIR"}, tests=f"""
{status(200)}
pm.test("In repair", () => pm.expect(pm.response.json().status).to.eql("IN_REPAIR"));
"""),
        req("Same status again is a conflict", "PUT", "/api/admin/assets/{{assetB}}/status", token="adminToken", body={"status": "IN_REPAIR"}, tests=status(409)),
        req("An asset in repair cannot be assigned", "POST", "/api/admin/assets/{{assetB}}/assign", token="adminToken", body={"employeeUsername": "{{newUser}}"}, tests=status(409)),
        req("A missing status is rejected", "PUT", "/api/admin/assets/{{assetB}}/status", token="adminToken", body={}, tests=status(400)),
        req("An assigned asset cannot be moved", "PUT", "/api/admin/assets/{{assetA}}/status", token="adminToken", body={"status": "IN_REPAIR"}, tests=f"""
{status(400)}
pm.test("Must be returned first", () => pm.expect(pm.response.json().message).to.include("returned"));
"""),
        req("Repaired", "PUT", "/api/admin/assets/{{assetB}}/status", token="adminToken", body={"status": "AVAILABLE"}, tests=status(200)),
        req("Retire the monitor", "PUT", "/api/admin/assets/{{assetB}}/status", token="adminToken", body={"status": "RETIRED"}, tests=status(200)),
        req("A retired asset cannot come back", "PUT", "/api/admin/assets/{{assetB}}/status", token="adminToken", body={"status": "AVAILABLE"}, tests=status(409)),
        req("A retired asset is read-only", "PUT", "/api/admin/assets/{{assetB}}", token="adminToken", body=asset("{{tagB}}", "{{serialB}}", "Renamed", "MONITOR"), tests=status(400)),
        req("Unknown asset is 404", "GET", "/api/admin/assets/999999", token="adminToken", tests=status(404)),
    ]),
    folder("7. Employees and the dashboard", [
        req("Employees with the number of assets they hold", "GET", "/api/admin/employees?search={{newUser}}", token="adminToken", tests=f"""
{status(200)}
pm.test("One employee, nothing held now", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].activeAssets).to.eql(0); }});
"""),
        req("Dashboard", "GET", "/api/admin/dashboard", token="adminToken", tests=f"""
{status(200)}
pm.test("Counts, value and breakdowns", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("totalAssets", "available", "assigned", "returnRequests", "inRepair", "retired", "overdue", "poorCondition", "assetValue", "byCategory", "byStatus", "oldestRequests");
  pm.expect(d.totalAssets).to.be.at.least(50);
  pm.expect(d.retired).to.be.at.least(1);
}});
"""),
    ]),
    folder("8. Security checks", [
        req("No token is 401", "GET", "/api/my/assets", tests=status(401)),
        req("Employee cannot see the register", "GET", "/api/admin/assets", token="employeeToken", tests=status(403)),
        req("Employee cannot see the dashboard", "GET", "/api/admin/dashboard", token="employeeToken", tests=status(403)),
        req("Employee cannot list employees", "GET", "/api/admin/employees", token="employeeToken", tests=status(403)),
        req("Employee cannot change a status", "PUT", "/api/admin/assets/{{assetA}}/status", token="employeeToken", body={"status": "RETIRED"}, tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Asset Allocation API",
    "Walks the asset workflow: registering assets with unique tags and serials, search, assigning to one employee at a time, return requests, "
    "accepting returns into the available pool, assignment history, repair and retirement, the dashboard and security.\n"
    "Start the backend first (http://localhost:8115). Repeatable: it registers a fresh employee and assets with a unique tag and serial each run.",
    items, {"baseUrl": "http://localhost:8115", **{k: "" for k in [
        "adminToken", "employeeToken", "otherToken", "runId", "newUser", "tagA", "tagB", "tagAlower", "serialA", "serialB", "serialAlower",
        "tomorrow", "nextMonth", "today", "assetA", "assetB", "assignment1"]}})
