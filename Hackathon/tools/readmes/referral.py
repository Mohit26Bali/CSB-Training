"""python tools/readmes/referral.py referral/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Internal Job Referral Portal",
    "summary": "Employees browse open positions and refer candidates; recruiters publish jobs, review referrals and move them through "
               "the hiring pipeline. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8109, "frontend_port": 5109, "db": "job_referrals", "package": "referral",
    "logins": [("`recruiter`", "`recruiter123`", "RECRUITER"), ("`anil.kapoor`", "`recruiter123`", "RECRUITER"),
               ("`aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (12 employees)", "`employee123`", "EMPLOYEE")],
    "features": [
        "**Employee:** browse open positions (search by title, team, location or skill, and filter by skill); refer a candidate with name, "
        "email, optional phone, resume link and a note; track every referral on a pipeline view with the recruiter's notes.",
        "**Recruiter:** create jobs as drafts, edit, publish, close (and reopen) them; review referrals by stage, job or text; move a referral "
        "one stage at a time (SUBMITTED → SCREENING → INTERVIEW → SELECTED) or reject it, with an optional note; a dashboard with referral counts "
        "per stage and per job, open jobs and hires.",
        "Search jobs by skill, a recruiter dashboard and referral counts (the case's bonus features).",
    ],
    "rules": [
        ("Only PUBLISHED jobs accept referrals (drafts and closed jobs do not, and drafts are invisible to employees)", "`400`"),
        ("A candidate cannot be referred twice for the same job, even by a different employee (email compared ignoring case; also a unique constraint)", "`409`"),
        ("Only recruiters change a referral's status", "`403` for employees"),
        ("The pipeline moves one step at a time: no skipping, no repeating, no going back; REJECTED is possible from any unfinished stage", "`409`, the message lists what is allowed"),
        ("SELECTED and REJECTED are final", "`409`"),
        ("Closing a job stops new referrals; existing referrals can still be processed. A closed job can be reopened", "`400` for new referrals"),
        ("Employees see only their own referrals; recruiters cannot refer", "`403` / filtered by user"),
        ("Name and a valid email are mandatory; phone and resume link are validated when given", "`400` with a field error each"),
    ],
    "concurrency": "referring the same candidate from several tabs or by several employees at once creates one referral (the unique constraint "
                   "settles the race and the losers get a clear `409`); every status change locks the referral row under `READ_COMMITTED`, so two "
                   "recruiters cannot both advance the same referral. The tests check both with parallel threads.",
    "notes": [
        "Each referral response includes `allowedNext`, the statuses it can move to (the next stage first, `REJECTED` last), so the UI only offers legal moves.",
    ],
    "api": [
        ("GET", "/api/jobs?search=&skill=", "any", "Published jobs"),
        ("GET", "/api/jobs/skills", "any", "Skills used by published jobs"),
        ("POST", "/api/referrals", "EMPLOYEE", "Refer `{jobId, candidateName, candidateEmail, candidatePhone?, resumeUrl?, notes?}`"),
        ("GET", "/api/referrals/my?status=", "EMPLOYEE", "Own referrals"),
        ("GET", "/api/recruiter/jobs?status=", "RECRUITER", "All jobs with referral counts"),
        ("POST", "/api/recruiter/jobs", "RECRUITER", "Create a draft `{title, department, location, description, skills[]}`"),
        ("PUT", "/api/recruiter/jobs/{id}", "RECRUITER", "Edit a job"),
        ("PUT", "/api/recruiter/jobs/{id}/publish", "RECRUITER", "Publish (or reopen) a job"),
        ("PUT", "/api/recruiter/jobs/{id}/close", "RECRUITER", "Close a published job"),
        ("GET", "/api/recruiter/referrals?jobId=&status=&search=", "RECRUITER", "Referrals"),
        ("PUT", "/api/recruiter/referrals/{id}/status", "RECRUITER", "Move `{status, note?}`"),
        ("GET", "/api/recruiter/dashboard", "RECRUITER", "Counts per stage and per job"),
    ],
    "controllers": "AuthController, JobController (everyone), ReferralController (employee), RecruiterController",
    "services": "JobService, ReferralService (pipeline rules, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository, JobRepository (row lock), ReferralRepository (row lock)",
    "entities": "User, Job, Referral, Role, JobStatus, ReferralStatus (knows its legal next stages)",
    "tables": "`users`, `jobs` (status, skills) and `referrals` (job and referrer FKs, **UNIQUE (job, candidate email)**, status, recruiter note).",
    "tests": "25 tests on in-memory H2 (no MySQL needed): publishing rules, duplicates, the pipeline, search by skill, roles, dashboard, concurrency",
    "collection": "Referral.postman_collection.json",
    "postman": "49 requests (78 assertions). It registers a fresh employee and uses unique job titles and candidate emails, so it can be run many times",
    "limits": [
        "Resumes are links, not uploads.",
        "There is no referral bonus tracking or notification email when a referral changes stage.",
        "Searching and filtering is done in memory (Java Streams), which is fine for one company's open roles but would need paging at scale.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
