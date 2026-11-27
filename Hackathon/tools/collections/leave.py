"""python tools/collections/leave.py leave-portal/docs/postman/LeavePortal.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE_DATES = """
// All test dates are in NEXT year, so they never collide with the seeded leave (which is all in the current year).
const iso = d => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const add = (d, n) => { const t = new Date(d); t.setDate(t.getDate() + n); return t; };
let base = new Date(new Date().getFullYear() + 1, 0, 1);
while (base.getDay() !== 1) base = add(base, 1);          // first Monday of next year
base = add(base, 7 * Math.floor(Math.random() * 30));       // a random week, so repeated runs rarely share dates
const set = (k, v) => pm.collectionVariables.set(k, iso(v));
set("w1mon", base); set("w1tue", add(base, 1)); set("w1wed", add(base, 2)); set("w1fri", add(base, 4));
set("w1sat", add(base, 5)); set("w1sun", add(base, 6)); set("w1nextmon", add(base, 7));
set("w2mon", add(base, 7)); set("w2tue", add(base, 8));
set("w3mon", add(base, 14)); set("w3tue", add(base, 15));
set("w4mon", add(base, 21)); set("w4nextfri", add(base, 32));  // 10 working days
pm.collectionVariables.set("month", iso(base).slice(0, 7));
pm.collectionVariables.set("year", String(base.getFullYear()));
pm.collectionVariables.set("newUser", "postman." + Math.floor(Math.random() * 900000 + 100000));
"""


def leave(type_, frm, to, reason="Time off"):
    return {"leaveType": type_, "startDate": frm, "endDate": to, "reason": reason}


items = [
    folder("1. Auth", [
        req("Login as manager", "POST", "/api/auth/login", body={"username": "manager", "password": "manager123"},
            tests=login_tests("managerToken"), pre=PRE_DATES),
        req("Login as second manager", "POST", "/api/auth/login", body={"username": "neha.kapoor", "password": "manager123"},
            tests=login_tests("manager2Token")),
        req("Register a fresh employee for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Tester", "role": "MANAGER"}, tests=f"""
{status(201)}
pm.test("Always an employee", () => pm.expect(pm.response.json().role).to.eql("EMPLOYEE"));
pm.collectionVariables.set("employeeToken", pm.response.json().token);
"""),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "manager", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Balance", [
        req("Leave types", "GET", "/api/leave/types", token="employeeToken", tests=f"""
{status(200)}
pm.test("Four types, unpaid is untracked", () => {{
  const t = pm.response.json();
  pm.expect(t.length).to.eql(4);
  pm.expect(t.find(x => x.value === "UNPAID").tracked).to.be.false;
}});
"""),
        req("Fresh employee has the full allowance", "GET", "/api/leave/balance?year={{year}}", token="employeeToken", tests=f"""
{status(200)}
pm.test("Annual 18, sick 10, casual 6", () => {{
  const b = Object.fromEntries(pm.response.json().map(x => [x.leaveType, x]));
  pm.expect(b.ANNUAL.available).to.eql(18);
  pm.expect(b.SICK.available).to.eql(10);
  pm.expect(b.CASUAL.available).to.eql(6);
}});
"""),
    ]),
    folder("3. Request leave", [
        req("Valid request (3 working days)", "POST", "/api/leave", token="employeeToken",
            body=leave("ANNUAL", "{{w1mon}}", "{{w1wed}}"), tests=f"""
{status(201)}
const l = pm.response.json();
pm.test("Pending, 3 working days", () => {{ pm.expect(l.status).to.eql("PENDING"); pm.expect(l.days).to.eql(3); }});
pm.collectionVariables.set("leaveId", l.id);
"""),
        req("Days are reserved, not yet used", "GET", "/api/leave/balance?year={{year}}", token="employeeToken", tests=f"""
{status(200)}
pm.test("3 pending, 15 available", () => {{
  const a = pm.response.json().find(x => x.leaveType === "ANNUAL");
  pm.expect(a.pending).to.eql(3); pm.expect(a.used).to.eql(0); pm.expect(a.available).to.eql(15);
}});
"""),
        req("Weekend-only range is rejected", "POST", "/api/leave", token="employeeToken",
            body=leave("ANNUAL", "{{w1sat}}", "{{w1sun}}"), tests=f"""
{status(400)}
pm.test("Start date field error", () => pm.expect(pm.response.json().fieldErrors.startDate).to.include("no working days"));
"""),
        req("End before start is rejected", "POST", "/api/leave", token="employeeToken",
            body=leave("ANNUAL", "{{w1wed}}", "{{w1mon}}"), tests=f"""
{status(400)}
pm.test("End date field error", () => pm.expect(pm.response.json().fieldErrors.endDate).to.include("before"));
"""),
        req("Unknown type is rejected", "POST", "/api/leave", token="employeeToken",
            body=leave("SABBATICAL", "{{w2mon}}", "{{w2tue}}"), tests=f"""
{status(400)}
pm.test("Leave type field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("leaveType"));
"""),
        req("Missing fields list every problem", "POST", "/api/leave", token="employeeToken",
            body={"leaveType": "", "reason": ""}, tests=f"""
{status(400)}
pm.test("All bad fields are listed", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("leaveType", "startDate", "endDate", "reason"));
"""),
        req("Overlapping a pending request is a conflict", "POST", "/api/leave", token="employeeToken",
            body=leave("SICK", "{{w1wed}}", "{{w1fri}}"), tests=f"""
{status(409)}
pm.test("Explains the overlap", () => pm.expect(pm.response.json().message).to.include("already have"));
"""),
        req("More than the balance is rejected", "POST", "/api/leave", token="employeeToken",
            body=leave("CASUAL", "{{w4mon}}", "{{w4nextfri}}"), tests=f"""
{status(400)}
pm.test("Says not enough left", () => pm.expect(pm.response.json().message).to.include("Not enough"));
"""),
        req("Unpaid leave has no limit", "POST", "/api/leave", token="employeeToken",
            body=leave("UNPAID", "{{w4mon}}", "{{w4nextfri}}"), tests=f"""
{status(201)}
pm.test("10 working days", () => pm.expect(pm.response.json().days).to.eql(10));
pm.collectionVariables.set("unpaidId", pm.response.json().id);
"""),
    ]),
    folder("4. Employee views", [
        req("My leave lists both requests", "GET", "/api/leave/my", token="employeeToken", tests=f"""
{status(200)}
pm.test("Two requests, all mine", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("Filter by status and type", "GET", "/api/leave/my?status=PENDING&type=unpaid", token="employeeToken", tests=f"""
{status(200)}
pm.test("Only the unpaid one", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].leaveType).to.eql("UNPAID"); }});
"""),
        req("Cancel a pending request", "DELETE", "/api/leave/{{unpaidId}}", token="employeeToken", tests=f"""
{status(200)}
pm.test("Cancelled", () => pm.expect(pm.response.json().status).to.eql("CANCELLED"));
"""),
    ]),
    folder("5. Manager decides", [
        req("Pending list includes the request", "GET", "/api/manager/leave?status=PENDING&search=postman", token="managerToken", tests=f"""
{status(200)}
pm.test("Contains it", () => pm.expect(pm.response.json().some(l => l.id == pm.collectionVariables.get("leaveId"))).to.be.true);
"""),
        req("Approve with a note", "PUT", "/api/manager/leave/{{leaveId}}/approve", token="managerToken",
            body={"note": "Enjoy"}, tests=f"""
{status(200)}
pm.test("Approved, recorded, noted", () => {{
  const l = pm.response.json();
  pm.expect(l.status).to.eql("APPROVED"); pm.expect(l.decidedBy).to.eql("manager"); pm.expect(l.decisionNote).to.eql("Enjoy");
}});
"""),
        req("Balance is now deducted", "GET", "/api/leave/balance?year={{year}}", token="employeeToken", tests=f"""
{status(200)}
pm.test("3 used, 15 available, nothing pending", () => {{
  const a = pm.response.json().find(x => x.leaveType === "ANNUAL");
  pm.expect(a.used).to.eql(3); pm.expect(a.pending).to.eql(0); pm.expect(a.available).to.eql(15);
}});
"""),
        req("Approving again is a conflict", "PUT", "/api/manager/leave/{{leaveId}}/approve", token="manager2Token", tests=status(409)),
        req("Overlapping approved leave is now blocked", "POST", "/api/leave", token="employeeToken",
            body=leave("CASUAL", "{{w1mon}}", "{{w1mon}}"), tests=status(409)),
        req("Unknown request is 404", "PUT", "/api/manager/leave/999999/approve", token="managerToken", tests=status(404)),
    ]),
    folder("6. Managers and their own leave", [
        req("Manager requests leave for themselves", "POST", "/api/leave", token="managerToken",
            body=leave("ANNUAL", "{{w3mon}}", "{{w3tue}}"), tests=f"""
{status(201)}
pm.collectionVariables.set("ownLeaveId", pm.response.json().id);
"""),
        req("Manager cannot approve their own request", "PUT", "/api/manager/leave/{{ownLeaveId}}/approve", token="managerToken", tests=f"""
{status(403)}
pm.test("Explains why", () => pm.expect(pm.response.json().message).to.include("your own"));
"""),
        req("Manager cannot reject their own request either", "PUT", "/api/manager/leave/{{ownLeaveId}}/reject", token="managerToken", tests=status(403)),
        req("Another manager rejects it (no reason needed)", "PUT", "/api/manager/leave/{{ownLeaveId}}/reject", token="manager2Token", tests=f"""
{status(200)}
pm.test("Rejected", () => pm.expect(pm.response.json().status).to.eql("REJECTED"));
"""),
    ]),
    folder("7. Team calendar", [
        req("Approved leave shows on the calendar", "GET", "/api/manager/leave/calendar?month={{month}}", token="managerToken", tests=f"""
{status(200)}
pm.test("Contains the approved request only", () => {{
  const ids = pm.response.json().map(l => l.id);
  pm.expect(ids).to.include(pm.collectionVariables.get("leaveId"));
  pm.expect(ids).to.not.include(pm.collectionVariables.get("ownLeaveId"));
}});
"""),
        req("Bad month is a 400", "GET", "/api/manager/leave/calendar?month=nope", token="managerToken", tests=status(400)),
    ]),
    folder("8. Cancel approved leave", [
        req("Someone else cannot cancel it", "DELETE", "/api/leave/{{leaveId}}", token="managerToken", tests=status(404)),
        req("Employee cancels future approved leave", "DELETE", "/api/leave/{{leaveId}}", token="employeeToken", tests=f"""
{status(200)}
pm.test("Cancelled", () => pm.expect(pm.response.json().status).to.eql("CANCELLED"));
"""),
        req("The days are returned", "GET", "/api/leave/balance?year={{year}}", token="employeeToken", tests=f"""
{status(200)}
pm.test("Back to 18 available", () => pm.expect(pm.response.json().find(x => x.leaveType === "ANNUAL").available).to.eql(18));
"""),
        req("A rejected request cannot be cancelled", "DELETE", "/api/leave/{{ownLeaveId}}", token="managerToken", tests=status(409)),
    ]),
    folder("9. Security checks", [
        req("No token is 401", "GET", "/api/leave/my", tests=status(401)),
        req("Employee cannot list all requests", "GET", "/api/manager/leave", token="employeeToken", tests=status(403)),
        req("Employee cannot approve", "PUT", "/api/manager/leave/{{ownLeaveId}}/approve", token="employeeToken", tests=status(403)),
        req("Employee cannot see the team calendar", "GET", "/api/manager/leave/calendar", token="employeeToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Leave Portal API",
    "Walks the whole leave workflow: balances, validation, overlap, approval, own-request protection, calendar and cancellation.\n"
    "Start the backend first (http://localhost:8112), then run with the Collection Runner. The run is repeatable: "
    "it uses next year's dates and a freshly registered employee.",
    items, {"baseUrl": "http://localhost:8112", **{k: "" for k in [
        "managerToken", "manager2Token", "employeeToken", "leaveId", "unpaidId", "ownLeaveId", "newUser", "month", "year",
        "w1mon", "w1tue", "w1wed", "w1fri", "w1sat", "w1sun", "w1nextmon", "w2mon", "w2tue", "w3mon", "w3tue", "w4mon", "w4nextfri"]}})
