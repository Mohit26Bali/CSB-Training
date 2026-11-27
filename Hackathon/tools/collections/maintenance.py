"""python tools/collections/maintenance.py maintenance/docs/postman/Maintenance.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("place", "Postman Block " + id);
pm.collectionVariables.set("techName", "Postman Tech " + id);
"""


def request(category="PLUMBING", priority="HIGH", location="{{place}}, Flat 1", description="Created by the Postman run"):
    return {"category": category, "description": description, "location": location, "priority": priority}


items = [
    folder("1. Auth", [
        req("Login as facility manager", "POST", "/api/auth/login", body={"username": "facility", "password": "manager123"},
            tests=login_tests("managerToken"), pre=PRE),
        req("Register a fresh resident for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Resident", "role": "FACILITY_MANAGER"}, tests=f"""
{status(201)}
pm.test("Always a resident", () => pm.expect(pm.response.json().role).to.eql("RESIDENT"));
pm.collectionVariables.set("residentToken", pm.response.json().token);
"""),
        req("Login as another resident", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "resident123"},
            tests=login_tests("resident2Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "facility", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Resident reports problems", [
        req("Categories and priorities", "GET", "/api/requests/meta", token="residentToken", tests=f"""
{status(200)}
pm.test("Eight categories, four priorities", () => {{ const m = pm.response.json(); pm.expect(m.categories.length).to.eql(8); pm.expect(m.priorities).to.eql(["LOW", "MEDIUM", "HIGH", "EMERGENCY"]); }});
"""),
        req("Empty form lists every problem", "POST", "/api/requests", token="residentToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("category", "description", "location", "priority"));
"""),
        req("Location is mandatory", "POST", "/api/requests", token="residentToken", body=request(location=""), tests=f"""
{status(400)}
pm.test("Location error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("location"));
"""),
        req("Description is mandatory", "POST", "/api/requests", token="residentToken", body=request(description=""), tests=f"""
{status(400)}
pm.test("Description error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("description"));
"""),
        req("Unknown category is rejected", "POST", "/api/requests", token="residentToken", body=request(category="magic"), tests=status(400)),
        req("Report a high priority leak", "POST", "/api/requests", token="residentToken", body=request(), tests=f"""
{status(201)}
const r = pm.response.json();
pm.test("Open and fresh, with a reference and no technician", () => {{ pm.expect(r.status).to.eql("OPEN"); pm.expect(r.number).to.match(/^MR-\\d+$/); pm.expect(r.aging).to.eql("FRESH"); pm.expect(r.technicianName).to.eql(null); pm.expect(r.allowedNext).to.eql(["ASSIGNED"]); }});
pm.collectionVariables.set("requestId", r.id);
pm.collectionVariables.set("requestNumber", r.number);
"""),
        req("Report a low priority garden problem", "POST", "/api/requests", token="residentToken", body=request("GARDEN", "low", "{{place}}, Park"), tests=f"""
{status(201)}
pm.collectionVariables.set("request2Id", pm.response.json().id);
"""),
        req("Managers cannot report", "POST", "/api/requests", token="managerToken", body=request(), tests=status(403)),
    ]),
    folder("3. Residents see only their own", [
        req("My requests", "GET", "/api/requests/my", token="residentToken", tests=f"""
{status(200)}
pm.test("Exactly my two", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("Search by reference", "GET", "/api/requests/my?search={{requestNumber}}", token="residentToken", tests=f"""
{status(200)}
pm.test("Found", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Another resident sees none of them", "GET", "/api/requests/my?search={{runId}}", token="resident2Token", tests=f"""
{status(200)}
pm.test("Empty", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("Another resident cannot open mine", "GET", "/api/requests/{{requestId}}", token="resident2Token", tests=status(403)),
        req("Open my own", "GET", "/api/requests/{{requestId}}", token="residentToken", tests=status(200)),
        req("Unknown request is 404", "GET", "/api/requests/999999", token="managerToken", tests=status(404)),
    ]),
    folder("4. Technicians", [
        req("Empty form lists the problems", "POST", "/api/manager/technicians", token="managerToken", body={}, tests=f"""
{status(400)}
pm.test("Name and trade", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("name", "trade"));
"""),
        req("A bad phone number is rejected", "POST", "/api/manager/technicians", token="managerToken", body={"name": "{{techName}}", "trade": "plumbing", "phone": "abc"}, tests=f"""
{status(400)}
pm.test("Phone error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("phone"));
"""),
        req("Add a technician", "POST", "/api/manager/technicians", token="managerToken", body={"name": "{{techName}}", "trade": "plumbing", "phone": "+91 98450 00000"}, tests=f"""
{status(201)}
const t = pm.response.json();
pm.test("Plumber, active, no jobs", () => {{ pm.expect(t.trade).to.eql("PLUMBING"); pm.expect(t.active).to.be.true; pm.expect(t.activeJobs).to.eql(0); }});
pm.collectionVariables.set("techId", t.id);
"""),
        req("The same name is rejected (case ignored)", "POST", "/api/manager/technicians", token="managerToken", body={"name": "postman tech {{runId}}", "trade": "plumbing"}, tests=f"""
{status(400)}
pm.test("Name error", () => pm.expect(pm.response.json().fieldErrors.name).to.include("already on the list"));
"""),
        req("Residents cannot see technicians", "GET", "/api/manager/technicians", token="residentToken", tests=status(403)),
    ]),
    folder("5. Assign, work and resolve", [
        req("The queue shows the leak, emergencies and old ones first", "GET", "/api/manager/requests?search={{place}}", token="managerToken", tests=f"""
{status(200)}
pm.test("High before low", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].priority).to.eql("HIGH"); }});
"""),
        req("Assign without a technician is rejected", "PUT", "/api/manager/requests/{{requestId}}/assign", token="managerToken", body={}, tests=status(400)),
        req("Unknown technician is rejected", "PUT", "/api/manager/requests/{{requestId}}/assign", token="managerToken", body={"technicianId": 999999}, tests=status(400)),
        req("Moving to assigned needs a technician", "PUT", "/api/manager/requests/{{requestId}}/status", token="managerToken", body={"status": "ASSIGNED"}, tests=status(400)),
        req("An open request cannot be started", "PUT", "/api/manager/requests/{{requestId}}/status", token="managerToken", body={"status": "IN_PROGRESS"}, tests=status(409)),
        req("Assign the technician", "PUT", "/api/manager/requests/{{requestId}}/assign", token="managerToken", body={"technicianId": "{{techId}}"}, tests=f"""
{status(200)}
pm.test("Assigned", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("ASSIGNED"); pm.expect(r.technicianName).to.eql(pm.collectionVariables.get("techName")); }});
"""),
        req("The resident sees who is coming", "GET", "/api/requests/my?search={{requestNumber}}", token="residentToken", tests=f"""
{status(200)}
pm.test("Assigned technician visible", () => pm.expect(pm.response.json()[0].technicianName).to.eql(pm.collectionVariables.get("techName")));
"""),
        req("The technician now has a job", "GET", "/api/manager/technicians", token="managerToken", tests=f"""
{status(200)}
pm.test("One active job", () => pm.expect(pm.response.json().find(t => t.id == pm.collectionVariables.get("techId")).activeJobs).to.eql(1));
"""),
        req("A busy technician cannot be made unavailable", "PUT", "/api/manager/technicians/{{techId}}/active?value=false", token="managerToken", tests=status(400)),
        req("Resolving before starting is a conflict", "PUT", "/api/manager/requests/{{requestId}}/status", token="managerToken", body={"status": "RESOLVED", "notes": "x"}, tests=status(409)),
        req("Start work", "PUT", "/api/manager/requests/{{requestId}}/status", token="managerToken", body={"status": "IN_PROGRESS"}, tests=f"""
{status(200)}
pm.test("In progress", () => pm.expect(pm.response.json().status).to.eql("IN_PROGRESS"));
"""),
        req("Starting again is a conflict", "PUT", "/api/manager/requests/{{requestId}}/status", token="managerToken", body={"status": "IN_PROGRESS"}, tests=status(409)),
        req("Resolution notes are mandatory", "PUT", "/api/manager/requests/{{requestId}}/status", token="managerToken", body={"status": "RESOLVED"}, tests=f"""
{status(400)}
pm.test("Notes error", () => pm.expect(pm.response.json().fieldErrors.notes).to.include("Resolution notes"));
"""),
        req("Resolve with notes", "PUT", "/api/manager/requests/{{requestId}}/status", token="managerToken", body={"status": "RESOLVED", "notes": "Replaced the corroded joint"}, tests=f"""
{status(200)}
pm.test("Resolved, no aging", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("RESOLVED"); pm.expect(r.resolutionNotes).to.eql("Replaced the corroded joint"); pm.expect(r.aging).to.eql(null); }});
"""),
        req("The resident reads what was done", "GET", "/api/requests/my?search={{requestNumber}}", token="residentToken", tests=f"""
{status(200)}
pm.test("Visible", () => pm.expect(pm.response.json()[0].resolutionNotes).to.eql("Replaced the corroded joint"));
"""),
        req("Close it", "PUT", "/api/manager/requests/{{requestId}}/status", token="managerToken", body={"status": "CLOSED"}, tests=f"""
{status(200)}
pm.test("Closed, nothing further", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("CLOSED"); pm.expect(r.allowedNext).to.eql([]); }});
"""),
        req("A closed request cannot be changed", "PUT", "/api/manager/requests/{{requestId}}/status", token="managerToken", body={"status": "IN_PROGRESS"}, tests=status(409)),
        req("A closed request cannot be assigned", "PUT", "/api/manager/requests/{{requestId}}/assign", token="managerToken", body={"technicianId": "{{techId}}"}, tests=status(409)),
        req("Residents cannot assign", "PUT", "/api/manager/requests/{{request2Id}}/assign", token="residentToken", body={"technicianId": "{{techId}}"}, tests=status(403)),
    ]),
    folder("6. Filters, technician availability and dashboard", [
        req("Filter by category", "GET", "/api/manager/requests?category=GARDEN&search={{place}}", token="managerToken", tests=f"""
{status(200)}
pm.test("Only the garden one", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Filter by technician", "GET", "/api/manager/requests?technicianId={{techId}}", token="managerToken", tests=f"""
{status(200)}
pm.test("Only the closed leak", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].status).to.eql("CLOSED"); }});
"""),
        req("Filter by age", "GET", "/api/manager/requests?aging=fresh&search={{place}}", token="managerToken", tests=f"""
{status(200)}
pm.test("The open garden one is fresh", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Make the technician unavailable (no jobs left)", "PUT", "/api/manager/technicians/{{techId}}/active?value=false", token="managerToken", tests=f"""
{status(200)}
pm.test("Unavailable", () => pm.expect(pm.response.json().active).to.be.false);
"""),
        req("An unavailable technician cannot be assigned", "PUT", "/api/manager/requests/{{request2Id}}/assign", token="managerToken", body={"technicianId": "{{techId}}"}, tests=f"""
{status(400)}
pm.test("Says not available", () => pm.expect(pm.response.json().message).to.include("not available"));
"""),
        req("Dashboard", "GET", "/api/manager/dashboard", token="managerToken", tests=f"""
{status(200)}
pm.test("Statuses, aging, categories and technician load", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("open", "assigned", "inProgress", "resolved", "closed", "fresh", "aging", "stale", "emergencies", "averageResolutionHours", "byCategory", "technicianLoad");
  pm.expect(d.byCategory.length).to.eql(8);
}});
"""),
    ]),
    folder("7. Security checks", [
        req("No token is 401", "GET", "/api/requests/my", tests=status(401)),
        req("Resident cannot see the manager queue", "GET", "/api/manager/requests", token="residentToken", tests=status(403)),
        req("Resident cannot change a status", "PUT", "/api/manager/requests/{{request2Id}}/status", token="residentToken", body={"status": "IN_PROGRESS"}, tests=status(403)),
        req("Resident cannot see the dashboard", "GET", "/api/manager/dashboard", token="residentToken", tests=status(403)),
        req("Manager has no personal request list", "GET", "/api/requests/my", token="managerToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Community Maintenance API",
    "Walks the maintenance workflow: reporting, own-only visibility, the technician directory, assignment, the status flow (ASSIGNED comes only from assigning, closed is final), mandatory resolution notes, aging, filters, the dashboard and security.\n"
    "Start the backend first (http://localhost:8110). Repeatable: it registers a fresh resident and uses a uniquely named location and technician each run.",
    items, {"baseUrl": "http://localhost:8110", **{k: "" for k in [
        "managerToken", "residentToken", "resident2Token", "requestId", "request2Id", "requestNumber", "techId", "runId", "newUser", "place", "techName"]}})
