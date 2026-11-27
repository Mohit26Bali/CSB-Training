"""python tools/readmes/scholarship.py scholarship/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Scholarship Application Management System",
    "summary": "Students browse scholarships and apply with their academic and financial details; administrators review the "
               "applications and approve or reject them with a reason. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8105, "frontend_port": 5105, "db": "scholarship_mgmt", "package": "scholarship",
    "logins": [("`scholarship`", "`admin123`", "SCHOLARSHIP_ADMIN"), ("`meena.krishnan`", "`admin123`", "SCHOLARSHIP_ADMIN"),
               ("`aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (14 students)", "`student123`", "STUDENT")],
    "features": [
        "**Student:** browse active scholarships (amount, deadline, criteria); apply with CGPA, family income, course, year and a "
        "statement, with a live eligibility preview; track every application, its status and the reason for the decision.",
        "**Administrator:** review applications (filter by scholarship and status, search by student or course) and approve or reject "
        "with a reason; create, edit and hide scholarships; statistics per scholarship (counts, approval rate, average score, total awarded).",
        "Eligibility score, filtering by scholarship and application statistics (the case's bonus features).",
    ],
    "rules": [
        ("A student cannot apply twice to the same scholarship (service check and a database unique constraint)", "`409`"),
        ("CGPA (0 to 10), family income, course, year (1 to 6) and a statement are all mandatory", "`400` with a field error each"),
        ("Closed (deadline passed) or hidden scholarships do not accept applications; the deadline day itself is still open", "`400`"),
        ("Only administrators approve or reject", "`403` for students"),
        ("Approving and rejecting both need a reason", "`400` with a field error"),
        ("A decided application cannot be decided again", "`409`"),
        ("Students see only their own applications; administrators cannot apply", "`403` / filtered by user"),
        ("Scholarship names are unique, ignoring case", "`400` with a field error"),
    ],
    "concurrency": "applying twice at the same moment (a double click) creates one application, because the unique constraint settles the race "
                   "and the loser gets a clear `409`; deciding takes a row lock under `READ_COMMITTED`, so two administrators cannot "
                   "both decide the same application. The tests check both with parallel threads.",
    "notes": [
        "**Eligibility score (0 to 100):** up to 60 points for academics (CGPA ÷ 10 × 60) plus up to 40 for need (falling in a straight line "
        "from 40 at zero income to 0 at the scholarship's income ceiling). \"Meets criteria\" is separate: CGPA at or above the minimum and income "
        "at or below the ceiling. An application that does not meet the criteria can still be submitted; the administrator decides.",
    ],
    "api": [
        ("GET", "/api/programs", "any", "Active scholarships (open ones flagged)"),
        ("POST", "/api/applications", "STUDENT", "Apply `{programId, gpa, annualFamilyIncome, course, yearOfStudy, statement}`"),
        ("GET", "/api/applications/my", "STUDENT", "Own applications with score and decision"),
        ("GET", "/api/admin/programs", "SCHOLARSHIP_ADMIN", "All scholarships with application counts"),
        ("POST", "/api/admin/programs", "SCHOLARSHIP_ADMIN", "Create a scholarship"),
        ("PUT", "/api/admin/programs/{id}", "SCHOLARSHIP_ADMIN", "Edit (or hide with `active: false`)"),
        ("GET", "/api/admin/applications?programId=&status=&search=", "SCHOLARSHIP_ADMIN", "Applications"),
        ("PUT", "/api/admin/applications/{id}/approve", "SCHOLARSHIP_ADMIN", "Approve with `{reason}`"),
        ("PUT", "/api/admin/applications/{id}/reject", "SCHOLARSHIP_ADMIN", "Reject with `{reason}`"),
        ("GET", "/api/admin/statistics", "SCHOLARSHIP_ADMIN", "Totals and per-scholarship statistics"),
    ],
    "controllers": "AuthController, ProgramController, ApplicationController (student), AdminController",
    "services": "ProgramService, ApplicationService (rules, statistics), EligibilityCalculator, AuthService, LoginAttemptService",
    "repos": "UserRepository, ScholarshipProgramRepository, ApplicationRepository (row lock)",
    "entities": "User, ScholarshipProgram, Application, Role, ApplicationStatus",
    "tables": "`users`, `scholarship_programs` (unique name), `applications` (student and programme FKs, **UNIQUE (student, programme)**, eligibility and decision fields).",
    "tests": "25 tests on in-memory H2 (no MySQL needed): eligibility maths, one application per scholarship, deadlines, reasons, visibility, roles, statistics, concurrency",
    "collection": "Scholarship.postman_collection.json",
    "postman": "42 requests (70 assertions). It registers a fresh student, creates uniquely named scholarships and hides them at the end, so it can be run many times",
    "limits": [
        "An application cannot be edited or withdrawn after it is submitted.",
        "The eligibility score is a simple formula, not a machine-learned ranking.",
        "Filtering and statistics are computed in memory (Java Streams), which is fine for one college but would need paging at scale.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
