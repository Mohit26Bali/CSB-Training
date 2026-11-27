"""python tools/collections/hostel.py hostel/docs/postman/Hostel.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("roomNo", "P-" + id);
pm.collectionVariables.set("roomNo2", "Q-" + id);
"""


def room(number="{{roomNo}}", floor=2, rtype="SINGLE", capacity=1, active=True):
    return {"number": number, "floor": floor, "type": rtype, "capacity": capacity, "active": active}


items = [
    folder("1. Auth", [
        req("Login as warden", "POST", "/api/auth/login", body={"username": "warden", "password": "admin123"},
            tests=login_tests("wardenToken"), pre=PRE),
        req("Register a fresh student for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Student", "role": "HOSTEL_ADMIN"}, tests=f"""
{status(201)}
pm.test("Always a student", () => pm.expect(pm.response.json().role).to.eql("STUDENT"));
pm.collectionVariables.set("studentToken", pm.response.json().token);
"""),
        req("Login as a seeded student with a room", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "student123"},
            tests=login_tests("residentToken")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "warden", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Warden creates rooms", [
        req("Create a single room with one bed", "POST", "/api/admin/rooms", token="wardenToken", body=room(), tests=f"""
{status(201)}
const r = pm.response.json();
pm.test("Free single room", () => {{ pm.expect(r.bedsLeft).to.eql(1); pm.expect(r.type).to.eql("SINGLE"); }});
pm.collectionVariables.set("roomId", r.id);
"""),
        req("Create a double room", "POST", "/api/admin/rooms", token="wardenToken", body=room("{{roomNo2}}", 3, "double", 2), tests=f"""
{status(201)}
pm.collectionVariables.set("room2Id", pm.response.json().id);
"""),
        req("Duplicate room number is rejected (case ignored)", "POST", "/api/admin/rooms", token="wardenToken", body=room("p-{{runId}}"), tests=f"""
{status(400)}
pm.test("Number error", () => pm.expect(pm.response.json().fieldErrors.number).to.include("already exists"));
"""),
        req("Empty form lists every problem", "POST", "/api/admin/rooms", token="wardenToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("number", "floor", "type", "capacity"));
"""),
        req("Capacity must be positive", "POST", "/api/admin/rooms", token="wardenToken", body=room("Z-1", capacity=0), tests=f"""
{status(400)}
pm.test("Capacity error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("capacity"));
"""),
        req("Unknown room type is rejected", "POST", "/api/admin/rooms", token="wardenToken", body=room("Z-2", rtype="CASTLE"), tests=f"""
{status(400)}
pm.test("Type error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("type"));
"""),
    ]),
    folder("3. Student browses and requests", [
        req("Student sees the new rooms", "GET", "/api/rooms?type=single", token="studentToken", tests=f"""
{status(200)}
pm.test("Contains the new single room, no occupant names", () => {{ const r = pm.response.json().find(x => x.id == pm.collectionVariables.get("roomId")); pm.expect(r.bedsLeft).to.eql(1); pm.expect(r.occupants).to.eql([]); }});
"""),
        req("Unknown type filter is rejected", "GET", "/api/rooms?type=palace", token="studentToken", tests=status(400)),
        req("No allocation yet (204)", "GET", "/api/allocation/my", token="studentToken", tests=status(204)),
        req("Request a room", "POST", "/api/requests", token="studentToken", body={"preferredType": "single", "note": "Quiet please"}, tests=f"""
{status(201)}
const r = pm.response.json();
pm.test("Pending with a queue position", () => {{ pm.expect(r.status).to.eql("PENDING"); pm.expect(r.queuePosition).to.be.a("number"); }});
pm.collectionVariables.set("requestId", r.id);
"""),
        req("A second pending request is a conflict", "POST", "/api/requests", token="studentToken", body={}, tests=status(409)),
        req("Unknown preferred type is rejected", "POST", "/api/requests", token="residentToken", body={"preferredType": "castle"}, tests=status(400)),
        req("A student who already has a room cannot ask", "POST", "/api/requests", token="residentToken", body={}, tests=status(409)),
        req("My requests", "GET", "/api/requests/my", token="studentToken", tests=f"""
{status(200)}
pm.test("Just my one", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
    ]),
    folder("4. Warden decides", [
        req("Pending requests include this one", "GET", "/api/admin/requests?status=PENDING", token="wardenToken", tests=f"""
{status(200)}
pm.test("Found, with the student's name", () => {{ const r = pm.response.json().find(x => x.id == pm.collectionVariables.get("requestId")); pm.expect(r.studentName).to.eql("Postman Student"); }});
"""),
        req("Approve without a room is rejected", "PUT", "/api/admin/requests/{{requestId}}/approve", token="wardenToken", body={}, tests=f"""
{status(400)}
pm.test("Room error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("roomId"));
"""),
        req("Unknown room is rejected", "PUT", "/api/admin/requests/{{requestId}}/approve", token="wardenToken", body={"roomId": 999999}, tests=status(400)),
        req("Unknown request is 404", "PUT", "/api/admin/requests/999999/approve", token="wardenToken", body={"roomId": "{{roomId}}"}, tests=status(404)),
        req("Close the single room for maintenance", "PUT", "/api/admin/rooms/{{roomId}}", token="wardenToken", body=room(active=False), tests=f"""
{status(200)}
pm.test("Closed", () => pm.expect(pm.response.json().active).to.be.false);
"""),
        req("A closed room cannot be allocated", "PUT", "/api/admin/requests/{{requestId}}/approve", token="wardenToken", body={"roomId": "{{roomId}}"}, tests=f"""
{status(400)}
pm.test("Mentions maintenance", () => pm.expect(pm.response.json().message).to.include("maintenance"));
"""),
        req("Reopen the room", "PUT", "/api/admin/rooms/{{roomId}}", token="wardenToken", body=room(active=True), tests=status(200)),
        req("Approve into the single room", "PUT", "/api/admin/requests/{{requestId}}/approve", token="wardenToken", body={"roomId": "{{roomId}}"}, tests=f"""
{status(200)}
pm.test("Approved with the room", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("APPROVED"); pm.expect(r.roomNumber).to.eql(pm.collectionVariables.get("roomNo")); pm.expect(r.decidedBy).to.eql("warden"); }});
"""),
        req("Approving again is a conflict", "PUT", "/api/admin/requests/{{requestId}}/approve", token="wardenToken", body={"roomId": "{{room2Id}}"}, tests=status(409)),
        req("Rejecting a decided request is a conflict", "PUT", "/api/admin/requests/{{requestId}}/reject", token="wardenToken", body={"reason": "x"}, tests=status(409)),
    ]),
    folder("5. Student sees the allocation", [
        req("My allocation", "GET", "/api/allocation/my", token="studentToken", tests=f"""
{status(200)}
pm.test("In the single room", () => {{ const a = pm.response.json(); pm.expect(a.roomNumber).to.eql(pm.collectionVariables.get("roomNo")); pm.expect(a.active).to.be.true; }});
pm.collectionVariables.set("allocationId", pm.response.json().id);
"""),
        req("The room is no longer offered", "GET", "/api/rooms?type=single", token="residentToken", tests=f"""
{status(200)}
pm.test("Full", () => pm.expect(pm.response.json().some(r => r.id == pm.collectionVariables.get("roomId"))).to.be.false);
"""),
        req("The student cannot ask again while living there", "POST", "/api/requests", token="studentToken", body={}, tests=status(409)),
        req("Room is shown full to the warden with the occupant", "GET", "/api/admin/rooms?type=single", token="wardenToken", tests=f"""
{status(200)}
pm.test("Occupied", () => {{ const r = pm.response.json().find(x => x.id == pm.collectionVariables.get("roomId")); pm.expect(r.occupied).to.eql(1); pm.expect(r.occupants).to.eql(["Postman Student"]); }});
"""),
        req("Editing the double room keeps its data", "PUT", "/api/admin/rooms/{{room2Id}}", token="wardenToken", body=room("{{roomNo2}}", 3, "double", 2), tests=status(200)),
    ]),
    folder("6. Rejections, cancelling and vacating", [
        req("A second student requests the same room", "POST", "/api/auth/register",
            body={"username": "{{newUser}}b", "password": "password123", "fullname": "Postman Student B"}, tests=f"""
{status(201)}
pm.collectionVariables.set("student2Token", pm.response.json().token);
"""),
        req("Student B asks", "POST", "/api/requests", token="student2Token", body={"note": "Please"}, tests=f"""
{status(201)}
pm.collectionVariables.set("request2Id", pm.response.json().id);
"""),
        req("A full room refuses student B", "PUT", "/api/admin/requests/{{request2Id}}/approve", token="wardenToken", body={"roomId": "{{roomId}}"}, tests=f"""
{status(409)}
pm.test("Says full", () => pm.expect(pm.response.json().message).to.include("full"));
"""),
        req("Rejection needs a reason", "PUT", "/api/admin/requests/{{request2Id}}/reject", token="wardenToken", body={}, tests=f"""
{status(400)}
pm.test("Reason error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("reason"));
"""),
        req("Another student cannot cancel B's request", "DELETE", "/api/requests/{{request2Id}}", token="studentToken", tests=status(403)),
        req("Reject with a reason", "PUT", "/api/admin/requests/{{request2Id}}/reject", token="wardenToken", body={"reason": "No single rooms left"}, tests=f"""
{status(200)}
pm.test("Rejected with the reason", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("REJECTED"); pm.expect(r.decisionReason).to.eql("No single rooms left"); }});
"""),
        req("Student B sees the reason", "GET", "/api/requests/my", token="student2Token", tests=f"""
{status(200)}
pm.test("Reason shown", () => pm.expect(pm.response.json()[0].decisionReason).to.eql("No single rooms left"));
"""),
        req("Student B asks again and cancels", "POST", "/api/requests", token="student2Token", body={}, tests=f"""
{status(201)}
pm.collectionVariables.set("request3Id", pm.response.json().id);
"""),
        req("Cancel the pending request", "DELETE", "/api/requests/{{request3Id}}", token="student2Token", tests=f"""
{status(200)}
pm.test("Cancelled", () => pm.expect(pm.response.json().status).to.eql("CANCELLED"));
"""),
        req("Cancelling again is a conflict", "DELETE", "/api/requests/{{request3Id}}", token="student2Token", tests=status(409)),
        req("Vacate the single room", "PUT", "/api/admin/allocations/{{allocationId}}/vacate", token="wardenToken", tests=f"""
{status(200)}
pm.test("Left", () => {{ pm.expect(pm.response.json().active).to.be.false; pm.expect(pm.response.json().vacatedAt).to.not.eql(null); }});
"""),
        req("Vacating again is a conflict", "PUT", "/api/admin/allocations/{{allocationId}}/vacate", token="wardenToken", tests=status(409)),
        req("The bed is free again", "GET", "/api/rooms?type=single", token="residentToken", tests=f"""
{status(200)}
pm.test("Offered again", () => pm.expect(pm.response.json().some(r => r.id == pm.collectionVariables.get("roomId"))).to.be.true);
"""),
        req("Residents list (current only)", "GET", "/api/admin/allocations", token="wardenToken", tests=f"""
{status(200)}
pm.test("Postman student left", () => pm.expect(pm.response.json().some(a => a.id == pm.collectionVariables.get("allocationId"))).to.be.false);
"""),
        req("Residents list with past residents", "GET", "/api/admin/allocations?activeOnly=false", token="wardenToken", tests=f"""
{status(200)}
pm.test("Postman student listed as left", () => {{ const a = pm.response.json().find(x => x.id == pm.collectionVariables.get("allocationId")); pm.expect(a.active).to.be.false; }});
"""),
        req("Dashboard", "GET", "/api/admin/dashboard", token="wardenToken", tests=f"""
{status(200)}
pm.test("Totals and by-type rows", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("rooms", "beds", "occupied", "free", "occupancyPercent", "pendingRequests", "fullRooms", "byType");
  pm.expect(d.occupied + d.free).to.eql(d.beds);
}});
"""),
    ]),
    folder("7. Security checks", [
        req("No token is 401", "GET", "/api/rooms", tests=status(401)),
        req("Student cannot list admin rooms", "GET", "/api/admin/rooms", token="studentToken", tests=status(403)),
        req("Student cannot approve", "PUT", "/api/admin/requests/{{requestId}}/approve", token="studentToken", body={"roomId": 1}, tests=status(403)),
        req("Student cannot see the dashboard", "GET", "/api/admin/dashboard", token="studentToken", tests=status(403)),
        req("Warden cannot request a room", "POST", "/api/requests", token="wardenToken", body={}, tests=status(403)),
        req("Warden has no personal allocation", "GET", "/api/allocation/my", token="wardenToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Hostel Room Allocation API",
    "Walks the hostel workflow: rooms, requests and the waiting list, approval with capacity checks, rejection reasons, cancelling, vacating and security.\n"
    "Start the backend first (http://localhost:8103). Repeatable: it registers fresh students and creates uniquely numbered rooms each run.",
    items, {"baseUrl": "http://localhost:8103", **{k: "" for k in [
        "wardenToken", "studentToken", "student2Token", "residentToken", "roomId", "room2Id", "requestId", "request2Id",
        "request3Id", "allocationId", "runId", "newUser", "roomNo", "roomNo2"]}})
