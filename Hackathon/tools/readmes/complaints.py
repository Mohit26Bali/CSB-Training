"""python tools/readmes/complaints.py complaints/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Customer Complaint Management Portal",
    "summary": "Customers file complaints and follow them on a timeline to a written resolution; support agents work a priority-ordered queue, "
               "assign, resolve and close. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8117, "frontend_port": 5117, "db": "complaint_portal", "package": "complaints",
    "logins": [("`agent`", "`agent123`", "SUPPORT_AGENT"), ("`kiran.bhat`, `zoya.khan`", "`agent123`", "SUPPORT_AGENT"),
               ("`aarav.sharma` (a complaint in every state, one waiting to be rated)", "`customer123`", "CUSTOMER"),
               ("`diya.patel`, `rohan.mehta`, … (30 customers)", "`customer123`", "CUSTOMER")],
    "features": [
        "**Customer:** file a complaint (subject, details, category, priority); see own complaints with a status filter and search by subject or "
        "reference (CMP-2042); open one to follow a timeline (filed, assigned, status changes), read the resolution and rate how it was handled with 1 to 5 stars.",
        "**Support agent:** a queue ordered by priority then waiting time, with filters for status, priority, category and assignee and search; "
        "start work in one click; assign and reassign; resolve with resolution notes, close; a statistics page with counts, resolution rate, "
        "average time to resolve, customer rating, a 7-day filed vs resolved chart, and breakdowns by priority and category.",
        "Priority, category filtering and complaint statistics (the case's bonus features).",
    ],
    "rules": [
        ("Subject, description, category and priority are mandatory; unknown categories and priorities are refused", "`400` with a field error each"),
        ("A customer can only view their own complaints", "`403`"),
        ("Only support agents change status or assignment, and only customers can file or rate", "`403`"),
        ("The flow is OPEN, IN_PROGRESS, RESOLVED, CLOSED, one step at a time; a resolved complaint can go back to IN_PROGRESS; closed is final", "`409`, the message lists what is allowed"),
        ("Resolution notes are mandatory to resolve; the customer sees them", "`400` field error"),
        ("A complaint can be rated only once, only by its owner, and only after it is resolved", "`409` / `403`"),
        ("Starting work takes the complaint for the agent if nobody owns it; work in progress cannot be left without an owner; only agents can be assignees", "automatic / `400`"),
    ],
    "concurrency": "every status, assignment and feedback change locks the complaint row (under `READ_COMMITTED`), so two agents resolving the "
                   "same complaint resolve it once and a double-clicked rating counts once. The tests check both with parallel threads.",
    "notes": [
        "The timeline is stored as events (filed, assigned, status changed, feedback), so it can never disagree with the complaint itself.",
        "The reference (CMP-2042) is the complaint id plus 2000. One page, `/complaints/{id}`, serves both roles, with agent controls only for agents.",
    ],
    "api": [
        ("POST", "/api/complaints", "CUSTOMER", "File `{subject, description, category, priority}`"),
        ("GET", "/api/complaints/my?status=&search=", "CUSTOMER", "Own complaints"),
        ("GET", "/api/complaints/meta", "any", "Categories and priorities"),
        ("GET", "/api/complaints/{id}", "owner / agents", "A complaint with its timeline"),
        ("POST", "/api/complaints/{id}/feedback", "CUSTOMER", "Rate `{rating 1-5, comment?}` (once, after resolution)"),
        ("GET", "/api/agent/complaints?status=&priority=&category=&assignee=&search=", "SUPPORT_AGENT", "The queue"),
        ("PUT", "/api/agent/complaints/{id}/assign", "SUPPORT_AGENT", "Assign `{assignee}` (empty = unassigned)"),
        ("PUT", "/api/agent/complaints/{id}/status", "SUPPORT_AGENT", "Move `{status, note?}` (note mandatory for RESOLVED)"),
        ("GET", "/api/agent/agents", "SUPPORT_AGENT", "Agents with their active load"),
        ("GET", "/api/agent/statistics", "SUPPORT_AGENT", "Counts, rating, breakdowns and the last 7 days"),
    ],
    "controllers": "AuthController, ComplaintController (everyone), AgentController",
    "services": "ComplaintService (complaints, queue, assignment, status flow, timeline, feedback, statistics), AuthService, LoginAttemptService",
    "repos": "UserRepository, ComplaintRepository (row lock), ComplaintEventRepository",
    "entities": "User, Complaint, ComplaintEvent, Role, Category, Priority, ComplaintStatus (knows its legal next steps), EventType",
    "tables": "`users`, `complaints` (customer and assignee FKs, status, resolution, rating) and `complaint_events` (the timeline).",
    "tests": "23 tests on in-memory H2 (no MySQL needed): filing, visibility, resolution notes, status flow, timeline, assignment, feedback, queue order, statistics, roles, parallel updates",
    "collection": "Complaints.postman_collection.json",
    "postman": "53 requests (84 assertions). It registers a fresh customer and creates uniquely titled complaints each run, so it can be run many times",
    "limits": [
        "No attachments, no email or SMS notifications to the customer.",
        "A closed complaint is final; a customer with a new problem files a new complaint.",
        "Filtering, search and the queue order are done in memory (Java Streams), which is fine for one company's complaints but would need paging at scale.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
