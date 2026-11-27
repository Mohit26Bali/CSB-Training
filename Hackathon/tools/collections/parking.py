"""python tools/collections/parking.py parking/docs/postman/Parking.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
const letters = () => String.fromCharCode(65 + Math.floor(Math.random() * 26)) + String.fromCharCode(65 + Math.floor(Math.random() * 26));
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("slotPrefix", "P" + id.slice(0, 4));
pm.collectionVariables.set("slotCode", "P" + id.slice(0, 4) + "-1");
pm.collectionVariables.set("slotCode2", "P" + id.slice(0, 4) + "-2");
pm.collectionVariables.set("vehicleA", "MH12" + letters() + id.slice(0, 4));
pm.collectionVariables.set("vehicleB", "MH14" + letters() + id.slice(2, 6));
"""


def slot(code="{{slotCode}}", floor=7, state=None):
    body = {"code": code, "floor": floor}
    if state:
        body["status"] = state
    return body


items = [
    folder("1. Auth", [
        req("Login as parking admin", "POST", "/api/auth/login", body={"username": "parkadmin", "password": "admin123"},
            tests=login_tests("adminToken"), pre=PRE),
        req("Register a fresh employee for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Employee", "role": "PARKING_ADMIN"}, tests=f"""
{status(201)}
pm.test("Always an employee", () => pm.expect(pm.response.json().role).to.eql("EMPLOYEE"));
pm.collectionVariables.set("empToken", pm.response.json().token);
"""),
        req("Login as an employee who already has a slot", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "employee123"},
            tests=login_tests("holderToken")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "parkadmin", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Admin creates slots", [
        req("Create a slot", "POST", "/api/admin/slots", token="adminToken", body=slot(), tests=f"""
{status(201)}
const s = pm.response.json();
pm.test("Available and free", () => {{ pm.expect(s.status).to.eql("AVAILABLE"); pm.expect(s.occupied).to.be.false; }});
pm.collectionVariables.set("slotId", s.id);
"""),
        req("Create a second slot", "POST", "/api/admin/slots", token="adminToken", body=slot("{{slotCode2}}"), tests=f"""
{status(201)}
pm.collectionVariables.set("slot2Id", pm.response.json().id);
"""),
        req("Duplicate code is rejected (case ignored)", "POST", "/api/admin/slots", token="adminToken", body=slot("{{slotCode}}"), tests=f"""
{status(400)}
pm.test("Code error", () => pm.expect(pm.response.json().fieldErrors.code).to.include("already exists"));
"""),
        req("Empty form lists every problem", "POST", "/api/admin/slots", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("Both fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("code", "floor"));
"""),
        req("Unknown status is rejected", "POST", "/api/admin/slots", token="adminToken", body=slot("ZZ-1", 7, "haunted"), tests=status(400)),
    ]),
    folder("3. Employee browses and requests", [
        req("Employee sees the free slots on that floor", "GET", "/api/slots?floor=7", token="empToken", tests=f"""
{status(200)}
pm.test("Both new slots, no occupant names", () => {{ const l = pm.response.json().filter(s => s.code.startsWith(pm.collectionVariables.get("slotPrefix"))); pm.expect(l.length).to.eql(2); pm.expect(l[0].occupantName).to.eql(null); }});
"""),
        req("No allocation yet (204)", "GET", "/api/parking/allocation/my", token="empToken", tests=status(204)),
        req("Invalid vehicle number is rejected", "POST", "/api/parking/requests", token="empToken", body={"vehicleNumber": "12345678"}, tests=f"""
{status(400)}
pm.test("Vehicle error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("vehicleNumber"));
"""),
        req("Missing vehicle number is rejected", "POST", "/api/parking/requests", token="empToken", body={}, tests=status(400)),
        req("Request a slot (spaces around the number are ignored)", "POST", "/api/parking/requests", token="empToken",
            body={"vehicleNumber": " {{vehicleA}} ", "preferredFloor": 7, "note": "Daily commuter"}, tests=f"""
{status(201)}
const r = pm.response.json();
pm.test("Pending, upper-case without spaces", () => {{ pm.expect(r.status).to.eql("PENDING"); pm.expect(r.vehicleNumber).to.eql(pm.collectionVariables.get("vehicleA")); }});
pm.collectionVariables.set("requestId", r.id);
"""),
        req("A second pending request is a conflict", "POST", "/api/parking/requests", token="empToken", body={"vehicleNumber": "{{vehicleB}}"}, tests=status(409)),
        req("Someone who already holds a slot cannot ask", "POST", "/api/parking/requests", token="holderToken", body={"vehicleNumber": "{{vehicleB}}"}, tests=status(409)),
        req("My requests", "GET", "/api/parking/requests/my", token="empToken", tests=f"""
{status(200)}
pm.test("Just my one", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
    ]),
    folder("4. Admin decides", [
        req("Pending requests include this one", "GET", "/api/admin/requests?status=PENDING", token="adminToken", tests=f"""
{status(200)}
pm.test("Found with the employee's name", () => {{ const r = pm.response.json().find(x => x.id == pm.collectionVariables.get("requestId")); pm.expect(r.employeeName).to.eql("Postman Employee"); }});
"""),
        req("Approve without a slot is rejected", "PUT", "/api/admin/requests/{{requestId}}/approve", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("Slot error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("slotId"));
"""),
        req("Unknown slot is rejected", "PUT", "/api/admin/requests/{{requestId}}/approve", token="adminToken", body={"slotId": 999999}, tests=status(400)),
        req("Unknown request is 404", "PUT", "/api/admin/requests/999999/approve", token="adminToken", body={"slotId": "{{slotId}}"}, tests=status(404)),
        req("Mark the first slot reserved", "PUT", "/api/admin/slots/{{slotId}}", token="adminToken", body=slot(state="RESERVED"), tests=f"""
{status(200)}
pm.test("Reserved", () => pm.expect(pm.response.json().status).to.eql("RESERVED"));
"""),
        req("A reserved slot cannot be allocated", "PUT", "/api/admin/requests/{{requestId}}/approve", token="adminToken", body={"slotId": "{{slotId}}"}, tests=f"""
{status(400)}
pm.test("Says reserved", () => pm.expect(pm.response.json().message).to.include("reserved"));
"""),
        req("Make it available again", "PUT", "/api/admin/slots/{{slotId}}", token="adminToken", body=slot(state="AVAILABLE"), tests=status(200)),
        req("Approve into the first slot", "PUT", "/api/admin/requests/{{requestId}}/approve", token="adminToken", body={"slotId": "{{slotId}}"}, tests=f"""
{status(200)}
pm.test("Approved with the slot", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("APPROVED"); pm.expect(r.slotCode).to.eql(pm.collectionVariables.get("slotCode")); pm.expect(r.decidedBy).to.eql("parkadmin"); }});
"""),
        req("Approving again is a conflict", "PUT", "/api/admin/requests/{{requestId}}/approve", token="adminToken", body={"slotId": "{{slot2Id}}"}, tests=status(409)),
        req("Rejecting a decided request is a conflict", "PUT", "/api/admin/requests/{{requestId}}/reject", token="adminToken", body={"reason": "x"}, tests=status(409)),
    ]),
    folder("5. Employee sees the allocation", [
        req("My allocation", "GET", "/api/parking/allocation/my", token="empToken", tests=f"""
{status(200)}
pm.test("Slot and vehicle", () => {{ const a = pm.response.json(); pm.expect(a.slotCode).to.eql(pm.collectionVariables.get("slotCode")); pm.expect(a.vehicleNumber).to.eql(pm.collectionVariables.get("vehicleA")); pm.expect(a.active).to.be.true; }});
pm.collectionVariables.set("allocationId", pm.response.json().id);
"""),
        req("The slot is no longer offered", "GET", "/api/slots?floor=7", token="holderToken", tests=f"""
{status(200)}
pm.test("Only the second slot is left", () => pm.expect(pm.response.json().map(s => s.code).filter(c => c.startsWith(pm.collectionVariables.get("slotPrefix")))).to.eql([pm.collectionVariables.get("slotCode2")]));
"""),
        req("Admin sees the occupant on the map", "GET", "/api/admin/slots?floor=7", token="adminToken", tests=f"""
{status(200)}
pm.test("Occupied by the Postman employee", () => {{ const s = pm.response.json().find(x => x.id == pm.collectionVariables.get("slotId")); pm.expect(s.occupied).to.be.true; pm.expect(s.occupantName).to.eql("Postman Employee"); }});
"""),
        req("An occupied slot cannot be closed", "PUT", "/api/admin/slots/{{slotId}}", token="adminToken", body=slot(state="MAINTENANCE"), tests=f"""
{status(400)}
pm.test("Asks to release first", () => pm.expect(pm.response.json().message).to.include("Release"));
"""),
    ]),
    folder("6. Queue, rejection, cancelling and release", [
        req("Register a second employee", "POST", "/api/auth/register",
            body={"username": "{{newUser}}b", "password": "password123", "fullname": "Postman Employee B"}, tests=f"""
{status(201)}
pm.collectionVariables.set("emp2Token", pm.response.json().token);
"""),
        req("Employee B asks with the same vehicle number", "POST", "/api/parking/requests", token="emp2Token", body={"vehicleNumber": "{{vehicleA}}"}, tests=f"""
{status(409)}
pm.test("Says already parked", () => pm.expect(pm.response.json().message).to.include("already parked"));
"""),
        req("Employee B asks with their own vehicle", "POST", "/api/parking/requests", token="emp2Token", body={"vehicleNumber": "{{vehicleB}}"}, tests=f"""
{status(201)}
pm.collectionVariables.set("request2Id", pm.response.json().id);
"""),
        req("A taken slot refuses employee B", "PUT", "/api/admin/requests/{{request2Id}}/approve", token="adminToken", body={"slotId": "{{slotId}}"}, tests=f"""
{status(409)}
pm.test("Says taken", () => pm.expect(pm.response.json().message).to.include("taken"));
"""),
        req("Rejection needs a reason", "PUT", "/api/admin/requests/{{request2Id}}/reject", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("Reason error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("reason"));
"""),
        req("Another employee cannot cancel B's request", "DELETE", "/api/parking/requests/{{request2Id}}", token="empToken", tests=status(403)),
        req("Reject with a reason", "PUT", "/api/admin/requests/{{request2Id}}/reject", token="adminToken", body={"reason": "Lot is full"}, tests=f"""
{status(200)}
pm.test("Rejected with the reason", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("REJECTED"); pm.expect(r.decisionReason).to.eql("Lot is full"); }});
"""),
        req("Employee B sees the reason", "GET", "/api/parking/requests/my", token="emp2Token", tests=f"""
{status(200)}
pm.test("Reason shown", () => pm.expect(pm.response.json()[0].decisionReason).to.eql("Lot is full"));
"""),
        req("Employee B asks again and cancels", "POST", "/api/parking/requests", token="emp2Token", body={"vehicleNumber": "{{vehicleB}}"}, tests=f"""
{status(201)}
pm.collectionVariables.set("request3Id", pm.response.json().id);
"""),
        req("Cancel the pending request", "DELETE", "/api/parking/requests/{{request3Id}}", token="emp2Token", tests=f"""
{status(200)}
pm.test("Cancelled", () => pm.expect(pm.response.json().status).to.eql("CANCELLED"));
"""),
        req("Cancelling again is a conflict", "DELETE", "/api/parking/requests/{{request3Id}}", token="emp2Token", tests=status(409)),
        req("Release the first slot", "PUT", "/api/admin/allocations/{{allocationId}}/release", token="adminToken", tests=f"""
{status(200)}
pm.test("Released by the admin", () => {{ const a = pm.response.json(); pm.expect(a.active).to.be.false; pm.expect(a.releasedBy).to.eql("parkadmin"); }});
"""),
        req("Releasing again is a conflict", "PUT", "/api/admin/allocations/{{allocationId}}/release", token="adminToken", tests=status(409)),
        req("Both slots are free again", "GET", "/api/slots?floor=7", token="holderToken", tests=f"""
{status(200)}
pm.test("Both slots free", () => pm.expect(pm.response.json().filter(s => s.code.startsWith(pm.collectionVariables.get("slotPrefix"))).length).to.eql(2));
"""),
        req("Allocations list (current only)", "GET", "/api/admin/allocations", token="adminToken", tests=f"""
{status(200)}
pm.test("Released one is hidden", () => pm.expect(pm.response.json().some(a => a.id == pm.collectionVariables.get("allocationId"))).to.be.false);
"""),
        req("Allocations list with released ones", "GET", "/api/admin/allocations?activeOnly=false", token="adminToken", tests=f"""
{status(200)}
pm.test("Listed as released", () => {{ const a = pm.response.json().find(x => x.id == pm.collectionVariables.get("allocationId")); pm.expect(a.active).to.be.false; }});
"""),
        req("Dashboard", "GET", "/api/admin/dashboard", token="adminToken", tests=f"""
{status(200)}
pm.test("Totals and floor rows", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("slots", "occupied", "free", "reserved", "maintenance", "occupancyPercent", "pendingRequests", "byFloor");
  pm.expect(d.byFloor.some(f => f.floor == 7 && f.free >= 2)).to.be.true;
}});
"""),
    ]),
    folder("7. Security checks", [
        req("No token is 401", "GET", "/api/slots", tests=status(401)),
        req("Employee cannot list admin slots", "GET", "/api/admin/slots", token="empToken", tests=status(403)),
        req("Employee cannot approve", "PUT", "/api/admin/requests/{{requestId}}/approve", token="empToken", body={"slotId": 1}, tests=status(403)),
        req("Employee cannot see the dashboard", "GET", "/api/admin/dashboard", token="empToken", tests=status(403)),
        req("Admin cannot request a slot", "POST", "/api/parking/requests", token="adminToken", body={"vehicleNumber": "{{vehicleB}}"}, tests=status(403)),
        req("Admin has no personal allocation", "GET", "/api/parking/allocation/my", token="adminToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Parking Slot Management API",
    "Walks the parking workflow: slots, requests and the queue, allocation rules (one slot per employee and per vehicle), rejection reasons, cancelling, releasing and security.\n"
    "Start the backend first (http://localhost:8106). Repeatable: it registers fresh employees and creates uniquely coded slots on floor 7 each run.",
    items, {"baseUrl": "http://localhost:8106", **{k: "" for k in [
        "adminToken", "empToken", "emp2Token", "holderToken", "slotId", "slot2Id", "requestId", "request2Id", "request3Id",
        "allocationId", "slotPrefix", "runId", "newUser", "slotCode", "slotCode2", "vehicleA", "vehicleB"]}})
