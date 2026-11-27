"""python tools/collections/scholarship.py scholarship/docs/postman/Scholarship.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const iso = (offset) => { const t = new Date(); t.setDate(t.getDate() + offset); return `${t.getFullYear()}-${String(t.getMonth() + 1).padStart(2, '0')}-${String(t.getDate()).padStart(2, '0')}`; };
pm.collectionVariables.set("runId", String(Math.floor(Math.random() * 900000 + 100000)));
pm.collectionVariables.set("newUser", "postman." + pm.collectionVariables.get("runId"));
pm.collectionVariables.set("future", iso(30));
pm.collectionVariables.set("yesterday", iso(-1));
"""


def program(name, deadline="{{future}}", min_gpa=7.0, max_income=800000, amount=25000):
    return {"name": name, "description": "Created by the Postman run", "amount": amount, "deadline": deadline,
            "minGpa": min_gpa, "maxFamilyIncome": max_income}


def application(program_id="{{programAId}}", gpa=9.0, income=200000, course="B.Tech CS", year=2, statement="Please consider me"):
    body = {"programId": program_id, "gpa": gpa, "annualFamilyIncome": income, "course": course, "yearOfStudy": year, "statement": statement}
    return body


items = [
    folder("1. Auth", [
        req("Login as administrator", "POST", "/api/auth/login", body={"username": "scholarship", "password": "admin123"},
            tests=login_tests("adminToken"), pre=PRE),
        req("Login as second administrator", "POST", "/api/auth/login", body={"username": "meena.krishnan", "password": "admin123"},
            tests=login_tests("admin2Token")),
        req("Register a fresh student for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Student", "role": "SCHOLARSHIP_ADMIN"}, tests=f"""
{status(201)}
pm.test("Always a student", () => pm.expect(pm.response.json().role).to.eql("STUDENT"));
pm.collectionVariables.set("studentToken", pm.response.json().token);
"""),
        req("Login as a seeded student", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "student123"},
            tests=login_tests("student2Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "scholarship", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Admin creates scholarships", [
        req("Create an open scholarship (A)", "POST", "/api/admin/programs", token="adminToken", body=program("Postman Grant A {{runId}}"), tests=f"""
{status(201)}
pm.test("Open and active", () => {{ const p = pm.response.json(); pm.expect(p.open).to.be.true; pm.expect(p.active).to.be.true; }});
pm.collectionVariables.set("programAId", pm.response.json().id);
"""),
        req("Create a demanding open scholarship (B)", "POST", "/api/admin/programs", token="adminToken",
            body=program("Postman Grant B {{runId}}", min_gpa=9.5, max_income=100000), tests=f"""
{status(201)}
pm.collectionVariables.set("programBId", pm.response.json().id);
"""),
        req("Create a scholarship whose deadline has passed (C)", "POST", "/api/admin/programs", token="adminToken",
            body=program("Postman Grant C {{runId}}", deadline="{{yesterday}}"), tests=f"""
{status(201)}
pm.test("Not open", () => pm.expect(pm.response.json().open).to.be.false);
pm.collectionVariables.set("programCId", pm.response.json().id);
"""),
        req("Duplicate name is rejected (ignoring case)", "POST", "/api/admin/programs", token="adminToken",
            body=program("postman grant a {{runId}}"), tests=f"""
{status(400)}
pm.test("Name field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("name"));
"""),
        req("Invalid details list every problem", "POST", "/api/admin/programs", token="adminToken",
            body={"name": "", "amount": 0, "minGpa": 11, "maxFamilyIncome": 0}, tests=f"""
{status(400)}
pm.test("All bad fields are listed", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("name", "amount", "deadline", "minGpa", "maxFamilyIncome"));
"""),
        req("Edit scholarship A", "PUT", "/api/admin/programs/{{programAId}}", token="adminToken",
            body={**program("Postman Grant A {{runId}}"), "description": "Edited", "amount": 30000}, tests=f"""
{status(200)}
pm.test("Updated", () => pm.expect(pm.response.json().amount).to.eql(30000));
"""),
    ]),
    folder("3. Student browses", [
        req("Active scholarships", "GET", "/api/programs", token="studentToken", tests=f"""
{status(200)}
pm.test("A and B are open, C is listed but closed", () => {{
  const byId = Object.fromEntries(pm.response.json().map(p => [p.id, p]));
  pm.expect(byId[pm.collectionVariables.get("programAId")].open).to.be.true;
  pm.expect(byId[pm.collectionVariables.get("programBId")].open).to.be.true;
  pm.expect(byId[pm.collectionVariables.get("programCId")].open).to.be.false;
}});
"""),
    ]),
    folder("4. Student applies", [
        req("Apply to A (meets the criteria)", "POST", "/api/applications", token="studentToken", body=application(), tests=f"""
{status(201)}
const a = pm.response.json();
pm.test("Submitted, score 84, meets criteria", () => {{ pm.expect(a.status).to.eql("SUBMITTED"); pm.expect(a.eligibilityScore).to.eql(84); pm.expect(a.meetsCriteria).to.be.true; }});
pm.collectionVariables.set("appAId", a.id);
"""),
        req("Apply to B (does not meet the criteria, still allowed)", "POST", "/api/applications", token="studentToken",
            body=application("{{programBId}}"), tests=f"""
{status(201)}
const a = pm.response.json();
pm.test("Score 54, does not meet criteria", () => {{ pm.expect(a.eligibilityScore).to.eql(54); pm.expect(a.meetsCriteria).to.be.false; }});
pm.collectionVariables.set("appBId", a.id);
"""),
        req("Applying to A again is a conflict", "POST", "/api/applications", token="studentToken", body=application(), tests=f"""
{status(409)}
pm.test("Explains", () => pm.expect(pm.response.json().message).to.include("already applied"));
"""),
        req("A closed scholarship refuses applications", "POST", "/api/applications", token="studentToken",
            body=application("{{programCId}}"), tests=f"""
{status(400)}
pm.test("Says it closed", () => pm.expect(pm.response.json().message).to.include("closed"));
"""),
        req("Mandatory fields are reported per field", "POST", "/api/applications", token="studentToken",
            body={"programId": "{{programAId}}"}, tests=f"""
{status(400)}
pm.test("Every eligibility field", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("gpa", "annualFamilyIncome", "course", "yearOfStudy", "statement"));
"""),
        req("Out-of-range values are rejected", "POST", "/api/applications", token="student2Token",
            body=application(gpa=11, income=-5, year=9), tests=f"""
{status(400)}
pm.test("CGPA, income and year errors", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("gpa", "annualFamilyIncome", "yearOfStudy"));
"""),
        req("Unknown scholarship is rejected", "POST", "/api/applications", token="studentToken", body=application(program_id=999999), tests=f"""
{status(400)}
pm.test("Programme field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("programId"));
"""),
    ]),
    folder("5. Student views", [
        req("My applications", "GET", "/api/applications/my", token="studentToken", tests=f"""
{status(200)}
pm.test("Exactly my two", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("Another student does not see them", "GET", "/api/applications/my", token="student2Token", tests=f"""
{status(200)}
pm.test("None of mine", () => pm.expect(pm.response.json().some(a => a.id == pm.collectionVariables.get("appAId"))).to.be.false);
"""),
    ]),
    folder("6. Admin decides", [
        req("Awaiting-decision list for scholarship A", "GET", "/api/admin/applications?status=SUBMITTED&programId={{programAId}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Contains the application", () => pm.expect(pm.response.json().some(a => a.id == pm.collectionVariables.get("appAId"))).to.be.true);
"""),
        req("Search by student", "GET", "/api/admin/applications?search={{newUser}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Finds both", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("Approve without a reason is 400", "PUT", "/api/admin/applications/{{appAId}}/approve", token="adminToken", body={"reason": ""}, tests=f"""
{status(400)}
pm.test("Reason field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("reason"));
"""),
        req("Approve with a reason", "PUT", "/api/admin/applications/{{appAId}}/approve", token="adminToken", body={"reason": "Strong record"}, tests=f"""
{status(200)}
pm.test("Approved, recorded, with reason", () => {{ const a = pm.response.json(); pm.expect(a.status).to.eql("APPROVED"); pm.expect(a.decidedBy).to.eql("scholarship"); pm.expect(a.decisionReason).to.eql("Strong record"); }});
"""),
        req("Deciding again is a conflict", "PUT", "/api/admin/applications/{{appAId}}/reject", token="admin2Token", body={"reason": "Changed my mind"}, tests=status(409)),
        req("Reject B with a reason", "PUT", "/api/admin/applications/{{appBId}}/reject", token="admin2Token", body={"reason": "CGPA is below the minimum"}, tests=f"""
{status(200)}
pm.test("Rejected", () => pm.expect(pm.response.json().status).to.eql("REJECTED"));
"""),
        req("Unknown application is 404", "PUT", "/api/admin/applications/999999/approve", token="adminToken", body={"reason": "x"}, tests=status(404)),
        req("Student reads both decisions and reasons", "GET", "/api/applications/my", token="studentToken", tests=f"""
{status(200)}
pm.test("Reasons visible", () => {{
  const reasons = pm.response.json().map(a => a.decisionReason);
  pm.expect(reasons).to.include("Strong record");
  pm.expect(reasons).to.include("CGPA is below the minimum");
}});
"""),
        req("Statistics", "GET", "/api/admin/statistics", token="adminToken", tests=f"""
{status(200)}
pm.test("Totals and per-programme rows", () => {{
  const s = pm.response.json();
  pm.expect(s).to.include.keys("totalApplications", "submitted", "approved", "rejected", "approvalRate", "totalAwarded", "byProgram");
  const a = s.byProgram.find(p => p.programId == pm.collectionVariables.get("programAId"));
  pm.expect(a.approved).to.eql(1);
  pm.expect(a.awardedAmount).to.eql(30000);
}});
"""),
        req("Admin programme list shows counts", "GET", "/api/admin/programs", token="adminToken", tests=f"""
{status(200)}
pm.test("A has one application, approved", () => {{
  const a = pm.response.json().find(p => p.id == pm.collectionVariables.get("programAId"));
  pm.expect(a.applicationCount).to.eql(1); pm.expect(a.approvedCount).to.eql(1);
}});
"""),
    ]),
    folder("7. Tidy up", [
        req("Hide scholarship A", "PUT", "/api/admin/programs/{{programAId}}", token="adminToken", body={**program("Postman Grant A {{runId}}"), "active": False}, tests=status(200)),
        req("Hide scholarship B", "PUT", "/api/admin/programs/{{programBId}}", token="adminToken", body={**program("Postman Grant B {{runId}}"), "active": False}, tests=status(200)),
        req("Hide scholarship C", "PUT", "/api/admin/programs/{{programCId}}", token="adminToken", body={**program("Postman Grant C {{runId}}", deadline="{{yesterday}}"), "active": False}, tests=status(200)),
        req("Hidden scholarships disappear for students", "GET", "/api/programs", token="studentToken", tests=f"""
{status(200)}
pm.test("A is no longer listed", () => pm.expect(pm.response.json().some(p => p.id == pm.collectionVariables.get("programAId"))).to.be.false);
"""),
    ]),
    folder("8. Security checks", [
        req("No token is 401", "GET", "/api/programs", tests=status(401)),
        req("Student cannot list applications", "GET", "/api/admin/applications", token="studentToken", tests=status(403)),
        req("Student cannot decide", "PUT", "/api/admin/applications/{{appBId}}/approve", token="studentToken", body={"reason": "me"}, tests=status(403)),
        req("Student cannot create scholarships", "POST", "/api/admin/programs", token="studentToken", body=program("Nope"), tests=status(403)),
        req("Student cannot see statistics", "GET", "/api/admin/statistics", token="studentToken", tests=status(403)),
        req("Administrator cannot apply", "POST", "/api/applications", token="adminToken", body=application(), tests=status(403)),
        req("Administrator has no personal applications list", "GET", "/api/applications/my", token="adminToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Scholarship Portal API",
    "Walks the scholarship workflow: programmes, applying, eligibility score, one application per scholarship, decisions with reasons, statistics and security.\n"
    "Start the backend first (http://localhost:8105). Repeatable: it registers a fresh student, creates uniquely named scholarships and hides them at the end.",
    items, {"baseUrl": "http://localhost:8105", **{k: "" for k in [
        "adminToken", "admin2Token", "studentToken", "student2Token", "programAId", "programBId", "programCId", "appAId", "appBId",
        "runId", "newUser", "future", "yesterday"]}})
