"""python tools/collections/helpdesk.py helpdesk/docs/postman/HelpDesk.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
"""


def ticket(title="Postman Ticket {{runId}}", category="NETWORK", priority="HIGH", description="Created by the Postman run"):
    return {"title": title, "description": description, "category": category, "priority": priority}


items = [
    folder("1. Auth", [
        req("Login as support agent", "POST", "/api/auth/login", body={"username": "support", "password": "agent123"},
            tests=login_tests("agentToken"), pre=PRE),
        req("Login as a second agent", "POST", "/api/auth/login", body={"username": "sanjay.rao", "password": "agent123"},
            tests=login_tests("agent2Token")),
        req("Register a fresh employee for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Employee", "role": "SUPPORT_AGENT"}, tests=f"""
{status(201)}
pm.test("Always an employee", () => pm.expect(pm.response.json().role).to.eql("EMPLOYEE"));
pm.collectionVariables.set("empToken", pm.response.json().token);
"""),
        req("Login as another employee", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "employee123"},
            tests=login_tests("emp2Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "support", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Employee raises tickets", [
        req("Categories and priorities with SLA hours", "GET", "/api/tickets/meta", token="empToken", tests=f"""
{status(200)}
pm.test("Four priorities, critical is 4 hours", () => {{ const m = pm.response.json(); pm.expect(m.priorities.length).to.eql(4); pm.expect(m.priorities.find(p => p.name == "CRITICAL").slaHours).to.eql(4); }});
"""),
        req("Empty form lists every problem", "POST", "/api/tickets", token="empToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("title", "description", "category", "priority"));
"""),
        req("Unknown category is rejected", "POST", "/api/tickets", token="empToken", body=ticket(category="plumbing"), tests=f"""
{status(400)}
pm.test("Category error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("category"));
"""),
        req("Raise a high priority ticket", "POST", "/api/tickets", token="empToken", body=ticket(), tests=f"""
{status(201)}
const t = pm.response.json();
pm.test("Open, with a reference and an 8 hour deadline", () => {{ pm.expect(t.status).to.eql("OPEN"); pm.expect(t.number).to.match(/^HD-\\d+$/); pm.expect(t.slaState).to.eql("ON_TRACK"); pm.expect(t.assigneeUsername).to.eql(null); }});
pm.collectionVariables.set("ticketId", t.id);
pm.collectionVariables.set("ticketNumber", t.number);
"""),
        req("Raise a low priority ticket", "POST", "/api/tickets", token="empToken", body=ticket("Postman Second {{runId}}", "SOFTWARE", "low"), tests=f"""
{status(201)}
pm.collectionVariables.set("ticket2Id", pm.response.json().id);
"""),
        req("Agents cannot raise tickets", "POST", "/api/tickets", token="agentToken", body=ticket(), tests=status(403)),
    ]),
    folder("3. Employee views", [
        req("My tickets", "GET", "/api/tickets/my", token="empToken", tests=f"""
{status(200)}
pm.test("Exactly my two", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("Search by reference", "GET", "/api/tickets/my?search={{ticketNumber}}", token="empToken", tests=f"""
{status(200)}
pm.test("Found", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Filter by status", "GET", "/api/tickets/my?status=IN_PROGRESS", token="empToken", tests=f"""
{status(200)}
pm.test("None in progress yet", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("Another employee does not see them", "GET", "/api/tickets/my?search={{runId}}", token="emp2Token", tests=f"""
{status(200)}
pm.test("Empty", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("Another employee cannot open my ticket", "GET", "/api/tickets/{{ticketId}}", token="emp2Token", tests=status(403)),
        req("Open my own ticket", "GET", "/api/tickets/{{ticketId}}", token="empToken", tests=f"""
{status(200)}
pm.test("With an empty conversation", () => {{ const d = pm.response.json(); pm.expect(d.ticket.title).to.include("Postman Ticket"); pm.expect(d.comments).to.eql([]); }});
"""),
        req("Unknown ticket is 404", "GET", "/api/tickets/999999", token="agentToken", tests=status(404)),
    ]),
    folder("4. Agent queue and assignment", [
        req("The ticket is in the unassigned queue", "GET", "/api/agent/tickets?assignee=unassigned&search={{runId}}", token="agentToken", tests=f"""
{status(200)}
pm.test("Both, most urgent (high) first", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].priority).to.eql("HIGH"); }});
"""),
        req("Priority filter", "GET", "/api/agent/tickets?priority=LOW&search={{runId}}", token="agentToken", tests=f"""
{status(200)}
pm.test("Only the low one", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Search by requester name", "GET", "/api/agent/tickets?search=Postman Employee", token="agentToken", tests=f"""
{status(200)}
pm.test("Found", () => pm.expect(pm.response.json().length).to.be.at.least(2));
"""),
        req("Cannot assign to an employee", "PUT", "/api/agent/tickets/{{ticketId}}/assign", token="agentToken", body={"assignee": "diya.patel"}, tests=f"""
{status(400)}
pm.test("Assignee error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("assignee"));
"""),
        req("Assign to the second agent", "PUT", "/api/agent/tickets/{{ticketId}}/assign", token="agentToken", body={"assignee": "sanjay.rao"}, tests=f"""
{status(200)}
pm.test("Assigned, still open", () => {{ const t = pm.response.json(); pm.expect(t.assigneeUsername).to.eql("sanjay.rao"); pm.expect(t.status).to.eql("OPEN"); }});
"""),
        req("Put it back in the queue", "PUT", "/api/agent/tickets/{{ticketId}}/assign", token="agentToken", body={"assignee": ""}, tests=f"""
{status(200)}
pm.test("Unassigned", () => pm.expect(pm.response.json().assigneeUsername).to.eql(null));
"""),
        req("Employees cannot assign", "PUT", "/api/agent/tickets/{{ticketId}}/assign", token="empToken", body={"assignee": "support"}, tests=status(403)),
        req("The agents list shows their load", "GET", "/api/agent/agents", token="agentToken", tests=f"""
{status(200)}
pm.test("At least two agents", () => pm.expect(pm.response.json().length).to.be.at.least(2));
"""),
    ]),
    folder("5. Status flow", [
        req("Skipping to resolved is a conflict", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={"status": "RESOLVED", "note": "x"}, tests=f"""
{status(409)}
pm.test("Says in progress", () => pm.expect(pm.response.json().message).to.include("in progress"));
"""),
        req("Unknown status is rejected", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={"status": "DONE"}, tests=status(400)),
        req("Missing status is rejected", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={}, tests=status(400)),
        req("Start work (takes the ticket)", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={"status": "IN_PROGRESS"}, tests=f"""
{status(200)}
pm.test("In progress and owned by the agent", () => {{ const t = pm.response.json(); pm.expect(t.status).to.eql("IN_PROGRESS"); pm.expect(t.assigneeUsername).to.eql("support"); }});
"""),
        req("Starting it again is a conflict", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agent2Token", body={"status": "IN_PROGRESS"}, tests=status(409)),
        req("Work in progress cannot be left without an owner", "PUT", "/api/agent/tickets/{{ticketId}}/assign", token="agentToken", body={"assignee": ""}, tests=status(400)),
        req("Resolving needs a note", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={"status": "RESOLVED"}, tests=f"""
{status(400)}
pm.test("Note error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("note"));
"""),
        req("Resolve it", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={"status": "RESOLVED", "note": "Replaced the faulty cable"}, tests=f"""
{status(200)}
pm.test("Resolved within the SLA", () => {{ const t = pm.response.json(); pm.expect(t.status).to.eql("RESOLVED"); pm.expect(t.resolution).to.eql("Replaced the faulty cable"); pm.expect(t.slaState).to.eql("MET"); pm.expect(t.allowedNext).to.eql(["CLOSED", "IN_PROGRESS"]); }});
"""),
        req("The employee sees the resolution", "GET", "/api/tickets/{{ticketId}}", token="empToken", tests=f"""
{status(200)}
pm.test("Visible", () => pm.expect(pm.response.json().ticket.resolution).to.eql("Replaced the faulty cable"));
"""),
        req("A resolved ticket can go back to in progress", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={"status": "IN_PROGRESS", "note": "Fix did not hold"}, tests=f"""
{status(200)}
pm.test("Back in progress, resolved time cleared", () => {{ const t = pm.response.json(); pm.expect(t.status).to.eql("IN_PROGRESS"); pm.expect(t.resolvedAt).to.eql(null); }});
"""),
        req("Resolve again", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={"status": "RESOLVED", "note": "Replaced the switch port"}, tests=status(200)),
        req("Close it", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={"status": "CLOSED"}, tests=f"""
{status(200)}
pm.test("Closed, nothing further allowed", () => {{ const t = pm.response.json(); pm.expect(t.status).to.eql("CLOSED"); pm.expect(t.allowedNext).to.eql([]); }});
"""),
        req("A closed ticket cannot be reopened", "PUT", "/api/agent/tickets/{{ticketId}}/status", token="agentToken", body={"status": "IN_PROGRESS"}, tests=f"""
{status(409)}
pm.test("Says so", () => pm.expect(pm.response.json().message).to.include("cannot be reopened"));
"""),
        req("A closed ticket cannot be reassigned", "PUT", "/api/agent/tickets/{{ticketId}}/assign", token="agentToken", body={"assignee": "sanjay.rao"}, tests=status(409)),
    ]),
    folder("6. Conversation and internal notes", [
        req("Empty message is rejected", "POST", "/api/tickets/{{ticket2Id}}/comments", token="empToken", body={}, tests=f"""
{status(400)}
pm.test("Body error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("body"));
"""),
        req("The employee adds a message", "POST", "/api/tickets/{{ticket2Id}}/comments", token="empToken", body={"body": "Happens every morning"}, tests=status(201)),
        req("Another employee cannot comment", "POST", "/api/tickets/{{ticket2Id}}/comments", token="emp2Token", body={"body": "me too"}, tests=status(403)),
        req("An employee cannot write internal notes", "POST", "/api/tickets/{{ticket2Id}}/comments", token="empToken", body={"body": "secret", "internal": True}, tests=status(403)),
        req("The agent adds an internal note", "POST", "/api/tickets/{{ticket2Id}}/comments", token="agentToken", body={"body": "Known issue with the licence server", "internal": True}, tests=f"""
{status(201)}
pm.test("Marked internal", () => pm.expect(pm.response.json().internal).to.be.true);
"""),
        req("The agent replies publicly", "POST", "/api/tickets/{{ticket2Id}}/comments", token="agentToken", body={"body": "We are on it"}, tests=status(201)),
        req("The employee sees two messages, not the note", "GET", "/api/tickets/{{ticket2Id}}", token="empToken", tests=f"""
{status(200)}
pm.test("No internal notes", () => {{ const c = pm.response.json().comments; pm.expect(c.length).to.eql(2); pm.expect(c.some(x => x.internal)).to.be.false; }});
"""),
        req("The agent sees all three", "GET", "/api/tickets/{{ticket2Id}}", token="agentToken", tests=f"""
{status(200)}
pm.test("Three, one internal", () => {{ const c = pm.response.json().comments; pm.expect(c.length).to.eql(3); pm.expect(c.filter(x => x.internal).length).to.eql(1); }});
"""),
        req("A closed ticket takes no comments", "POST", "/api/tickets/{{ticketId}}/comments", token="empToken", body={"body": "thanks"}, tests=status(409)),
    ]),
    folder("7. Dashboard", [
        req("Dashboard", "GET", "/api/agent/dashboard", token="agentToken", tests=f"""
{status(200)}
pm.test("Counts and breakdowns", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("open", "inProgress", "resolved", "closed", "unassigned", "mine", "breached", "atRisk", "averageResolutionHours", "activeByPriority", "byCategory");
  pm.expect(d.byCategory.length).to.eql(6);
  pm.expect(d.activeByPriority.length).to.eql(4);
}});
"""),
    ]),
    folder("8. Security checks", [
        req("No token is 401", "GET", "/api/tickets/my", tests=status(401)),
        req("Employee cannot see the queue", "GET", "/api/agent/tickets", token="empToken", tests=status(403)),
        req("Employee cannot change a status", "PUT", "/api/agent/tickets/{{ticket2Id}}/status", token="empToken", body={"status": "IN_PROGRESS"}, tests=status(403)),
        req("Employee cannot see the dashboard", "GET", "/api/agent/dashboard", token="empToken", tests=status(403)),
        req("Agent has no personal ticket list", "GET", "/api/tickets/my", token="agentToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "IT Help Desk API",
    "Walks the help-desk workflow: raising tickets, the urgency-ordered queue and SLA state, assignment, the one-step status flow (closed is final), comments with agent-only internal notes, the dashboard and security.\n"
    "Start the backend first (http://localhost:8114). Repeatable: it registers a fresh employee and creates uniquely titled tickets each run.",
    items, {"baseUrl": "http://localhost:8114", **{k: "" for k in [
        "agentToken", "agent2Token", "empToken", "emp2Token", "ticketId", "ticket2Id", "ticketNumber", "runId", "newUser"]}})
