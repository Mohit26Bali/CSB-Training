"""python tools/readmes/helpdesk.py helpdesk/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "IT Help Desk Ticket Management System",
    "summary": "Employees raise tickets for technical problems; support agents work a queue ordered by SLA urgency, assign and resolve them. "
               "Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8114, "frontend_port": 5114, "db": "help_desk", "package": "helpdesk",
    "logins": [("`support`", "`agent123`", "SUPPORT_AGENT"), ("`sanjay.rao`, `fatima.sheikh`", "`agent123`", "SUPPORT_AGENT"),
               ("`aarav.sharma` (a ticket in every state)", "`employee123`", "EMPLOYEE"),
               ("`diya.patel`, `rohan.mehta`, … (30 employees)", "`employee123`", "EMPLOYEE")],
    "features": [
        "**Employee:** raise a ticket (title, details, category, priority; the form shows the fix-within hours for each priority); "
        "see own tickets with filter by status and search by title or reference (HD-1042); open a ticket to follow progress on a "
        "OPEN, IN PROGRESS, RESOLVED, CLOSED tracker, read the resolution and reply in the conversation.",
        "**Support agent:** a queue ordered by urgency (tickets closest to, or past, their SLA deadline first) with filters for status, "
        "priority and assignee (anyone, me, unassigned, a named agent) and search; start work in one click; assign and reassign; move a ticket "
        "along the status flow; reply to the employee or leave internal notes only agents see; a dashboard with unassigned, mine, past SLA, "
        "at risk, average time to resolve, and breakdowns by priority and category.",
        "Priority filtering, ticket search and an SLA indicator (the case's bonus features).",
    ],
    "rules": [
        ("Title, description, category and priority are mandatory; unknown categories and priorities are refused", "`400` with a field error each"),
        ("Only agents change the assignment or the status; only employees raise tickets and only on their own behalf", "`403`"),
        ("The flow is OPEN, IN_PROGRESS, RESOLVED, CLOSED, one step at a time; a resolved ticket may go back to IN_PROGRESS if the fix did not hold", "`409`, the message lists what is allowed"),
        ("Closed tickets cannot be reopened, reassigned or commented on", "`409`"),
        ("Resolving needs a note describing the fix, which the employee sees as the resolution", "`400` field error"),
        ("Starting work takes the ticket for the agent if nobody owns it; work in progress cannot be left without an owner; only agents can be assignees", "automatic / `400`"),
        ("An employee sees only their own tickets and never the internal notes", "`403` / filtered"),
        ("SLA hours by priority: critical 4, high 8, medium 24, low 72. State: on track, at risk (under a quarter of the time left), breached; once resolved: met or missed", "computed"),
    ],
    "concurrency": "every status, assignment and comment change locks the ticket row (under `READ_COMMITTED`), so when several agents click Start "
                   "or two agents resolve the same ticket, exactly one wins and the others get a clear `409`. The tests check both with parallel threads.",
    "notes": [
        "The reference (HD-1042) is the ticket id plus 1000, so it never needs a separate counter.",
        "One page, `/tickets/{id}`, serves both roles: agents get the assignment and status controls on it, employees do not.",
    ],
    "api": [
        ("POST", "/api/tickets", "EMPLOYEE", "Raise `{title, description, category, priority}`"),
        ("GET", "/api/tickets/my?status=&search=", "EMPLOYEE", "Own tickets"),
        ("GET", "/api/tickets/meta", "any", "Categories and priorities with their SLA hours"),
        ("GET", "/api/tickets/{id}", "owner / agents", "A ticket with its conversation (no internal notes for employees)"),
        ("POST", "/api/tickets/{id}/comments", "owner / agents", "Add `{body, internal?}` (internal: agents only)"),
        ("GET", "/api/agent/tickets?status=&priority=&assignee=&search=", "SUPPORT_AGENT", "The queue"),
        ("PUT", "/api/agent/tickets/{id}/assign", "SUPPORT_AGENT", "Assign `{assignee}` (empty = unassigned)"),
        ("PUT", "/api/agent/tickets/{id}/status", "SUPPORT_AGENT", "Move `{status, note?}`"),
        ("GET", "/api/agent/agents", "SUPPORT_AGENT", "Agents with their active load"),
        ("GET", "/api/agent/dashboard", "SUPPORT_AGENT", "Counts, SLA problems and breakdowns"),
    ],
    "controllers": "AuthController, TicketController (everyone), AgentController",
    "services": "TicketService (tickets, queue, assignment, status flow, comments, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository, TicketRepository (row lock), TicketCommentRepository",
    "entities": "User, Ticket, TicketComment, Role, Category, Priority (knows its SLA hours), TicketStatus (knows its legal next steps)",
    "tables": "`users`, `tickets` (requester and assignee FKs, status, priority, SLA due time, resolution) and `ticket_comments` (internal-note flag).",
    "tests": "25 tests on in-memory H2 (no MySQL needed): creation, visibility, comments, status flow, assignment, queue order, SLA states, dashboard, roles, parallel updates",
    "collection": "HelpDesk.postman_collection.json",
    "postman": "55 requests (88 assertions). It registers a fresh employee and creates uniquely titled tickets each run, so it can be run many times",
    "limits": [
        "SLA hours run on the clock, not on working hours.",
        "No attachments or email notifications.",
        "Filtering, search and the queue order are done in memory (Java Streams), which is fine for one company's tickets but would need paging at scale.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
