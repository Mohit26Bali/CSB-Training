"""python tools/collections/referral.py referral/docs/postman/Referral.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("emailA", "asha." + id + "@example.com");
pm.collectionVariables.set("emailB", "bala." + id + "@example.com");
pm.collectionVariables.set("emailC", "chitra." + id + "@example.com");
"""


def job(title="Postman Engineer {{runId}}", skills=("Postman", "Testing", "postman")):
    return {"title": title, "department": "Quality", "location": "Remote", "description": "Created by the Postman run", "skills": list(skills)}


def referral(job_id="{{jobId}}", name="Asha Rao", email="{{emailA}}", **extra):
    return {"jobId": job_id, "candidateName": name, "candidateEmail": email, "notes": "Great fit", **extra}


items = [
    folder("1. Auth", [
        req("Login as recruiter", "POST", "/api/auth/login", body={"username": "recruiter", "password": "recruiter123"},
            tests=login_tests("recruiterToken"), pre=PRE),
        req("Login as second recruiter", "POST", "/api/auth/login", body={"username": "anil.kapoor", "password": "recruiter123"},
            tests=login_tests("recruiter2Token")),
        req("Register a fresh employee for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Employee", "role": "RECRUITER"}, tests=f"""
{status(201)}
pm.test("Always an employee", () => pm.expect(pm.response.json().role).to.eql("EMPLOYEE"));
pm.collectionVariables.set("employeeToken", pm.response.json().token);
"""),
        req("Login as a seeded employee", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "employee123"},
            tests=login_tests("employee2Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "recruiter", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Recruiter creates and publishes a job", [
        req("Create a job (starts as a draft)", "POST", "/api/recruiter/jobs", token="recruiterToken", body=job(), tests=f"""
{status(201)}
const j = pm.response.json();
pm.test("Draft, duplicate skills removed", () => {{ pm.expect(j.status).to.eql("DRAFT"); pm.expect(j.skills).to.eql(["Postman", "Testing"]); }});
pm.collectionVariables.set("jobId", j.id);
"""),
        req("Invalid job lists every problem", "POST", "/api/recruiter/jobs", token="recruiterToken",
            body={"title": "", "department": "", "location": "", "description": "", "skills": []}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("title", "department", "location", "description", "skills"));
"""),
        req("A draft is invisible to employees", "GET", "/api/jobs?search={{runId}}", token="employeeToken", tests=f"""
{status(200)}
pm.test("Not listed", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("A draft cannot take referrals", "POST", "/api/referrals", token="employeeToken", body=referral(), tests=f"""
{status(400)}
pm.test("Says it is not open", () => pm.expect(pm.response.json().message).to.include("not open"));
"""),
        req("Publish", "PUT", "/api/recruiter/jobs/{{jobId}}/publish", token="recruiterToken", tests=f"""
{status(200)}
pm.test("Published", () => pm.expect(pm.response.json().status).to.eql("PUBLISHED"));
"""),
        req("Publishing again is a conflict", "PUT", "/api/recruiter/jobs/{{jobId}}/publish", token="recruiter2Token", tests=status(409)),
        req("Employees now find it by text", "GET", "/api/jobs?search={{runId}}", token="employeeToken", tests=f"""
{status(200)}
pm.test("Found", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("...and by skill", "GET", "/api/jobs?skill=postman", token="employeeToken", tests=f"""
{status(200)}
pm.test("Found by skill, ignoring case", () => pm.expect(pm.response.json().some(j => j.id == pm.collectionVariables.get("jobId"))).to.be.true);
"""),
        req("Skill list includes it", "GET", "/api/jobs/skills", token="employeeToken", tests=f"""
{status(200)}
pm.test("Postman is a skill", () => pm.expect(pm.response.json()).to.include("Postman"));
"""),
    ]),
    folder("3. Employee refers a candidate", [
        req("Valid referral", "POST", "/api/referrals", token="employeeToken", body=referral(email="{{emailA}}"), tests=f"""
{status(201)}
const r = pm.response.json();
pm.test("Submitted, may move to screening or be rejected", () => {{ pm.expect(r.status).to.eql("SUBMITTED"); pm.expect(r.allowedNext).to.eql(["SCREENING", "REJECTED"]); }});
pm.collectionVariables.set("referralAId", r.id);
"""),
        req("Same candidate again is a conflict", "POST", "/api/referrals", token="employeeToken", body=referral(), tests=f"""
{status(409)}
pm.test("Explains", () => pm.expect(pm.response.json().message).to.include("already been referred"));
"""),
        req("Another employee cannot refer the same candidate (email case ignored)", "POST", "/api/referrals", token="employee2Token",
            body=referral(email="ASHA.{{runId}}@EXAMPLE.COM"), tests=status(409)),
        req("Missing fields are reported", "POST", "/api/referrals", token="employeeToken", body={}, tests=f"""
{status(400)}
pm.test("Field errors", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("jobId", "candidateName", "candidateEmail"));
"""),
        req("Bad email, phone and resume link are rejected", "POST", "/api/referrals", token="employeeToken",
            body=referral(email="nope", candidatePhone="abc", resumeUrl="ftp://x"), tests=f"""
{status(400)}
pm.test("Each field", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("candidateEmail", "candidatePhone", "resumeUrl"));
"""),
        req("Unknown job is rejected", "POST", "/api/referrals", token="employeeToken", body=referral(job_id=999999), tests=f"""
{status(400)}
pm.test("Job field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("jobId"));
"""),
        req("Second referral (B)", "POST", "/api/referrals", token="employeeToken",
            body=referral(name="Bala Menon", email="{{emailB}}", candidatePhone="+91 98765 43210", resumeUrl="https://example.com/bala.pdf"), tests=f"""
{status(201)}
pm.collectionVariables.set("referralBId", pm.response.json().id);
"""),
    ]),
    folder("4. Employee views", [
        req("My referrals", "GET", "/api/referrals/my", token="employeeToken", tests=f"""
{status(200)}
pm.test("Exactly my two", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("Another employee does not see them", "GET", "/api/referrals/my", token="employee2Token", tests=f"""
{status(200)}
pm.test("Not mine", () => pm.expect(pm.response.json().some(r => r.id == pm.collectionVariables.get("referralAId"))).to.be.false);
"""),
    ]),
    folder("5. Recruiter moves referrals through the pipeline", [
        req("New referrals waiting", "GET", "/api/recruiter/referrals?status=SUBMITTED&search={{runId}}", token="recruiterToken", tests=f"""
{status(200)}
pm.test("Both are there, with referrer details", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].referrerUsername).to.eql(pm.collectionVariables.get("newUser")); }});
"""),
        req("Skipping a stage is a conflict", "PUT", "/api/recruiter/referrals/{{referralAId}}/status", token="recruiterToken", body={"status": "INTERVIEW"}, tests=f"""
{status(409)}
pm.test("Says what is allowed", () => pm.expect(pm.response.json().message).to.include("screening or rejected"));
"""),
        req("Unknown status is rejected", "PUT", "/api/recruiter/referrals/{{referralAId}}/status", token="recruiterToken", body={"status": "HIRED"}, tests=status(400)),
        req("Missing status is rejected", "PUT", "/api/recruiter/referrals/{{referralAId}}/status", token="recruiterToken", body={}, tests=status(400)),
        req("Move to screening with a note", "PUT", "/api/recruiter/referrals/{{referralAId}}/status", token="recruiterToken",
            body={"status": "screening", "note": "Phone screen booked"}, tests=f"""
{status(200)}
pm.test("Screening, recorded", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("SCREENING"); pm.expect(r.updatedBy).to.eql("recruiter"); pm.expect(r.statusNote).to.eql("Phone screen booked"); }});
"""),
        req("Going backwards is a conflict", "PUT", "/api/recruiter/referrals/{{referralAId}}/status", token="recruiter2Token", body={"status": "SUBMITTED"}, tests=status(409)),
        req("Employee sees the progress and the note", "GET", "/api/referrals/my?status=SCREENING", token="employeeToken", tests=f"""
{status(200)}
pm.test("Visible to the referrer", () => pm.expect(pm.response.json()[0].statusNote).to.eql("Phone screen booked"));
"""),
        req("Move to interview", "PUT", "/api/recruiter/referrals/{{referralAId}}/status", token="recruiterToken", body={"status": "INTERVIEW"}, tests=status(200)),
        req("Select the candidate", "PUT", "/api/recruiter/referrals/{{referralAId}}/status", token="recruiterToken", body={"status": "SELECTED", "note": "Offer sent"}, tests=f"""
{status(200)}
pm.test("Selected, nothing further allowed", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("SELECTED"); pm.expect(r.allowedNext).to.eql([]); }});
"""),
        req("A selected referral is final", "PUT", "/api/recruiter/referrals/{{referralAId}}/status", token="recruiterToken", body={"status": "REJECTED"}, tests=status(409)),
        req("Reject referral B straight away", "PUT", "/api/recruiter/referrals/{{referralBId}}/status", token="recruiterToken", body={"status": "REJECTED", "note": "Not the right skills"}, tests=f"""
{status(200)}
pm.test("Rejected", () => pm.expect(pm.response.json().status).to.eql("REJECTED"));
"""),
        req("A rejected referral is final", "PUT", "/api/recruiter/referrals/{{referralBId}}/status", token="recruiterToken", body={"status": "SCREENING"}, tests=status(409)),
        req("Unknown referral is 404", "PUT", "/api/recruiter/referrals/999999/status", token="recruiterToken", body={"status": "SCREENING"}, tests=status(404)),
    ]),
    folder("6. Closing a job and the dashboard", [
        req("Close the job", "PUT", "/api/recruiter/jobs/{{jobId}}/close", token="recruiterToken", tests=f"""
{status(200)}
pm.test("Closed", () => pm.expect(pm.response.json().status).to.eql("CLOSED"));
"""),
        req("Closing again is a conflict", "PUT", "/api/recruiter/jobs/{{jobId}}/close", token="recruiterToken", tests=status(409)),
        req("A closed job takes no new referrals", "POST", "/api/referrals", token="employeeToken", body=referral(name="Chitra Nair", email="{{emailC}}"), tests=status(400)),
        req("Employees no longer see the job", "GET", "/api/jobs?search={{runId}}", token="employeeToken", tests=f"""
{status(200)}
pm.test("Hidden", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("Recruiter still sees it with its referral count", "GET", "/api/recruiter/jobs?status=CLOSED", token="recruiterToken", tests=f"""
{status(200)}
pm.test("Two referrals", () => {{ const j = pm.response.json().find(x => x.id == pm.collectionVariables.get("jobId")); pm.expect(j.referralCount).to.eql(2); }});
"""),
        req("Dashboard", "GET", "/api/recruiter/dashboard", token="recruiterToken", tests=f"""
{status(200)}
pm.test("Totals and per-job rows", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("openJobs", "totalReferrals", "hires", "byStatus", "byJob");
  pm.expect(d.byStatus.length).to.eql(5);
  const j = d.byJob.find(x => x.jobId == pm.collectionVariables.get("jobId"));
  pm.expect(j.total).to.eql(2); pm.expect(j.selected).to.eql(1); pm.expect(j.rejected).to.eql(1);
}});
"""),
    ]),
    folder("7. Security checks", [
        req("No token is 401", "GET", "/api/jobs", tests=status(401)),
        req("Employee cannot list all jobs", "GET", "/api/recruiter/jobs", token="employeeToken", tests=status(403)),
        req("Employee cannot move a referral", "PUT", "/api/recruiter/referrals/{{referralBId}}/status", token="employeeToken", body={"status": "SCREENING"}, tests=status(403)),
        req("Employee cannot create jobs", "POST", "/api/recruiter/jobs", token="employeeToken", body=job(), tests=status(403)),
        req("Employee cannot see the dashboard", "GET", "/api/recruiter/dashboard", token="employeeToken", tests=status(403)),
        req("Recruiter cannot refer candidates", "POST", "/api/referrals", token="recruiterToken", body=referral(), tests=status(403)),
        req("Recruiter has no personal referral list", "GET", "/api/referrals/my", token="recruiterToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Referral Portal API",
    "Walks the referral workflow: jobs (draft, publish, close), referrals, duplicate protection, the one-step-at-a-time pipeline, the dashboard and security.\n"
    "Start the backend first (http://localhost:8109). Repeatable: it registers a fresh employee and uses unique job titles and candidate emails.",
    items, {"baseUrl": "http://localhost:8109", **{k: "" for k in [
        "recruiterToken", "recruiter2Token", "employeeToken", "employee2Token", "jobId", "referralAId", "referralBId",
        "runId", "newUser", "emailA", "emailB", "emailC"]}})
