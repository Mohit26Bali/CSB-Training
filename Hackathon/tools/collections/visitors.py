"""python tools/collections/visitors.py visitors/docs/postman/Visitors.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
const digits = (n) => Array.from({ length: n }, () => Math.floor(Math.random() * 10)).join("");
const day = (offset) => { const d = new Date(Date.now() + offset * 864e5); return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0"); };
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("today", day(0));
pm.collectionVariables.set("tomorrow", day(1));
pm.collectionVariables.set("nextWeek", day(5));
pm.collectionVariables.set("yesterday", day(-1));
pm.collectionVariables.set("phoneA", "98" + digits(8));
pm.collectionVariables.set("phoneB", "97" + digits(8));
pm.collectionVariables.set("phoneC", "96" + digits(8));
pm.collectionVariables.set("nameA", "Postman Visitor A " + id);
pm.collectionVariables.set("nameB", "Postman Visitor B " + id);
pm.collectionVariables.set("nameC", "Postman Visitor C " + id);
"""


def visit(name="{{nameA}}", phone="{{phoneA}}", date="{{today}}", purpose="Client meeting", company="Acme Ltd", time=None):
    body = {"visitorName": name, "phone": phone, "company": company, "purpose": purpose, "expectedDate": date}
    if time:
        body["expectedTime"] = time
    return body


items = [
    folder("1. Auth", [
        req("Login as security officer", "POST", "/api/auth/login", body={"username": "security", "password": "guard123"},
            tests=login_tests("guardToken"), pre=PRE),
        req("Register a fresh employee for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Employee", "role": "SECURITY_ADMIN"}, tests=f"""
{status(201)}
pm.test("Always an employee", () => pm.expect(pm.response.json().role).to.eql("EMPLOYEE"));
pm.collectionVariables.set("empToken", pm.response.json().token);
"""),
        req("Login as another employee", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "employee123"},
            tests=login_tests("emp2Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "security", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Employee invites visitors", [
        req("Empty form lists every problem", "POST", "/api/visitors", token="empToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("visitorName", "phone", "purpose", "expectedDate"));
"""),
        req("A bad phone number is rejected", "POST", "/api/visitors", token="empToken", body=visit(phone="abc"), tests=f"""
{status(400)}
pm.test("Phone error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("phone"));
"""),
        req("A date in the past is rejected", "POST", "/api/visitors", token="empToken", body=visit(date="{{yesterday}}"), tests=f"""
{status(400)}
pm.test("Date error", () => pm.expect(pm.response.json().fieldErrors.expectedDate).to.include("past"));
"""),
        req("Invite a visitor for today", "POST", "/api/visitors", token="empToken", body=visit(time="11:30"), tests=f"""
{status(201)}
const v = pm.response.json();
pm.test("Expected today with a pass code", () => {{ pm.expect(v.status).to.eql("EXPECTED"); pm.expect(v.passCode).to.match(/^VP-\\d{{6}}$/); pm.expect(v.dueToday).to.be.true; pm.expect(v.canCheckIn).to.be.true; }});
pm.collectionVariables.set("visitId", v.id);
pm.collectionVariables.set("passCode", v.passCode);
"""),
        req("The same phone on the same day is a conflict", "POST", "/api/visitors", token="empToken", body=visit(name="Again"), tests=status(409)),
        req("Invite a visitor for tomorrow", "POST", "/api/visitors", token="empToken", body=visit("{{nameB}}", "{{phoneB}}", "{{tomorrow}}"), tests=f"""
{status(201)}
pm.test("Not due today", () => {{ const v = pm.response.json(); pm.expect(v.dueToday).to.be.false; pm.expect(v.canCheckIn).to.be.false; }});
pm.collectionVariables.set("visit2Id", pm.response.json().id);
"""),
        req("Invite a third visitor for later", "POST", "/api/visitors", token="empToken", body=visit("{{nameC}}", "{{phoneC}}", "{{nextWeek}}"), tests=f"""
{status(201)}
pm.collectionVariables.set("visit3Id", pm.response.json().id);
"""),
        req("Security cannot invite", "POST", "/api/visitors", token="guardToken", body=visit(), tests=status(403)),
    ]),
    folder("3. Employee views and edits their own", [
        req("My visitors", "GET", "/api/visitors/my?search={{runId}}", token="empToken", tests=f"""
{status(200)}
pm.test("Exactly my three", () => pm.expect(pm.response.json().length).to.eql(3));
"""),
        req("Search by phone", "GET", "/api/visitors/my?search={{phoneB}}", token="empToken", tests=f"""
{status(200)}
pm.test("Found", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Another employee sees none", "GET", "/api/visitors/my?search={{runId}}", token="emp2Token", tests=f"""
{status(200)}
pm.test("Empty", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("Another employee cannot edit mine", "PUT", "/api/visitors/{{visit3Id}}", token="emp2Token", body=visit("X", "{{phoneC}}", "{{nextWeek}}"), tests=status(403)),
        req("Edit the third invitation", "PUT", "/api/visitors/{{visit3Id}}", token="empToken", body=visit("{{nameC}} (renamed)", "{{phoneC}}", "{{nextWeek}}", purpose="Contract signing"), tests=f"""
{status(200)}
pm.test("Updated, same pass", () => {{ const v = pm.response.json(); pm.expect(v.purpose).to.eql("Contract signing"); pm.expect(v.visitorName).to.include("renamed"); }});
"""),
        req("Cancel the third invitation", "DELETE", "/api/visitors/{{visit3Id}}", token="empToken", tests=f"""
{status(200)}
pm.test("Cancelled", () => pm.expect(pm.response.json().status).to.eql("CANCELLED"));
"""),
        req("Cancelling again is a conflict", "DELETE", "/api/visitors/{{visit3Id}}", token="empToken", tests=status(409)),
        req("A cancelled invitation cannot be edited", "PUT", "/api/visitors/{{visit3Id}}", token="empToken", body=visit("X", "{{phoneC}}", "{{nextWeek}}"), tests=status(409)),
        req("Unknown invitation is 404", "DELETE", "/api/visitors/999999", token="empToken", tests=status(404)),
    ]),
    folder("4. Security at the gate", [
        req("Today's list includes the visitor", "GET", "/api/security/visitors?search={{runId}}&date={{today}}", token="guardToken", tests=f"""
{status(200)}
pm.test("Only today's one, with the host", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].hostName).to.eql("Postman Employee"); }});
"""),
        req("Search across all days by name", "GET", "/api/security/visitors?search={{nameB}}", token="guardToken", tests=f"""
{status(200)}
pm.test("Found for tomorrow", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Find by pass code", "GET", "/api/security/pass/{{passCode}}", token="guardToken", tests=f"""
{status(200)}
pm.test("The right visitor", () => pm.expect(pm.response.json().visitorName).to.eql(pm.collectionVariables.get("nameA")));
"""),
        req("An unknown pass code is 404", "GET", "/api/security/pass/VP-000000", token="guardToken", tests=status(404)),
        req("Employees cannot use the gate list", "GET", "/api/security/visitors", token="empToken", tests=status(403)),
        req("Cannot check out before check-in", "PUT", "/api/security/visitors/{{visitId}}/check-out", token="guardToken", tests=f"""
{status(409)}
pm.test("Says not checked in", () => pm.expect(pm.response.json().message).to.include("not been checked in"));
"""),
        req("Cannot check in tomorrow's visitor today", "PUT", "/api/security/visitors/{{visit2Id}}/check-in", token="guardToken", tests=f"""
{status(400)}
pm.test("Says not today", () => pm.expect(pm.response.json().message).to.include("not today"));
"""),
        req("Cannot check in a cancelled invitation", "PUT", "/api/security/visitors/{{visit3Id}}/check-in", token="guardToken", tests=status(409)),
        req("Employees cannot check in", "PUT", "/api/security/visitors/{{visitId}}/check-in", token="empToken", tests=status(403)),
        req("Check the visitor in", "PUT", "/api/security/visitors/{{visitId}}/check-in", token="guardToken", tests=f"""
{status(200)}
pm.test("Inside, recorded against the guard", () => {{ const v = pm.response.json(); pm.expect(v.status).to.eql("CHECKED_IN"); pm.expect(v.checkedInBy).to.eql("security"); pm.expect(v.canCheckOut).to.be.true; pm.expect(v.canCancel).to.be.false; }});
"""),
        req("Checking in twice is a conflict", "PUT", "/api/security/visitors/{{visitId}}/check-in", token="guardToken", tests=f"""
{status(409)}
pm.test("Says already inside", () => pm.expect(pm.response.json().message).to.include("already inside"));
"""),
        req("The employee sees the arrival", "GET", "/api/visitors/my?status=CHECKED_IN", token="empToken", tests=f"""
{status(200)}
pm.test("Checked in", () => pm.expect(pm.response.json().some(v => v.id == pm.collectionVariables.get("visitId"))).to.be.true);
"""),
        req("A visitor who is inside cannot be cancelled", "DELETE", "/api/visitors/{{visitId}}", token="empToken", tests=status(409)),
        req("Check the visitor out", "PUT", "/api/security/visitors/{{visitId}}/check-out", token="guardToken", tests=f"""
{status(200)}
pm.test("Checked out", () => {{ const v = pm.response.json(); pm.expect(v.status).to.eql("CHECKED_OUT"); pm.expect(v.checkedOutAt).to.be.a("string"); }});
"""),
        req("Checking out twice is a conflict", "PUT", "/api/security/visitors/{{visitId}}/check-out", token="guardToken", tests=status(409)),
        req("Unknown visitor is 404", "PUT", "/api/security/visitors/999999/check-in", token="guardToken", tests=status(404)),
    ]),
    folder("5. Daily report", [
        req("Statistics", "GET", "/api/security/stats", token="guardToken", tests=f"""
{status(200)}
pm.test("Numbers, a week and the busiest hosts", () => {{
  const s = pm.response.json();
  pm.expect(s).to.include.keys("date", "expectedToday", "notYetArrived", "insideNow", "checkedOutToday", "cancelledToday", "upcomingNextSevenDays", "noShowsYesterday", "averageVisitMinutes", "lastSevenDays", "busiestHostsToday");
  pm.expect(s.lastSevenDays.length).to.eql(7);
  pm.expect(s.checkedOutToday).to.be.at.least(1);
}});
"""),
        req("Employees cannot see the report", "GET", "/api/security/stats", token="empToken", tests=status(403)),
    ]),
    folder("6. Security checks", [
        req("No token is 401", "GET", "/api/visitors/my", tests=status(401)),
        req("Security has no personal visitor list", "GET", "/api/visitors/my", token="guardToken", tests=status(403)),
        req("Security cannot cancel invitations", "DELETE", "/api/visitors/{{visit2Id}}", token="guardToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Visitor Management API",
    "Walks the visitor workflow: invitations with validation and duplicate protection, own-only visibility, editing and cancelling, the gate list and pass lookup, check-in only on the day, no check-out before check-in, the daily report and security.\n"
    "Start the backend first (http://localhost:8104). Repeatable: it registers a fresh employee and uses random visitor phone numbers and names each run.",
    items, {"baseUrl": "http://localhost:8104", **{k: "" for k in [
        "guardToken", "empToken", "emp2Token", "visitId", "visit2Id", "visit3Id", "passCode", "runId", "newUser", "today", "tomorrow",
        "nextWeek", "yesterday", "phoneA", "phoneB", "phoneC", "nameA", "nameB", "nameC"]}})
