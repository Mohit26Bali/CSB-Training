"""python tools/collections/complaints.py complaints/docs/postman/Complaints.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
"""


def complaint(subject="Postman Complaint {{runId}}", category="BILLING", priority="HIGH", description="Created by the Postman run"):
    return {"subject": subject, "description": description, "category": category, "priority": priority}


items = [
    folder("1. Auth", [
        req("Login as support agent", "POST", "/api/auth/login", body={"username": "agent", "password": "agent123"},
            tests=login_tests("agentToken"), pre=PRE),
        req("Login as a second agent", "POST", "/api/auth/login", body={"username": "kiran.bhat", "password": "agent123"},
            tests=login_tests("agent2Token")),
        req("Register a fresh customer for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Customer", "role": "SUPPORT_AGENT"}, tests=f"""
{status(201)}
pm.test("Always a customer", () => pm.expect(pm.response.json().role).to.eql("CUSTOMER"));
pm.collectionVariables.set("custToken", pm.response.json().token);
"""),
        req("Login as another customer", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "customer123"},
            tests=login_tests("cust2Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "agent", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Customer files complaints", [
        req("Categories and priorities", "GET", "/api/complaints/meta", token="custToken", tests=f"""
{status(200)}
pm.test("Six categories, four priorities", () => {{ const m = pm.response.json(); pm.expect(m.categories.length).to.eql(6); pm.expect(m.priorities).to.eql(["LOW", "MEDIUM", "HIGH", "URGENT"]); }});
"""),
        req("Empty form lists every problem", "POST", "/api/complaints", token="custToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("subject", "description", "category", "priority"));
"""),
        req("Unknown category is rejected", "POST", "/api/complaints", token="custToken", body=complaint(category="weather"), tests=f"""
{status(400)}
pm.test("Category error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("category"));
"""),
        req("File a high priority complaint", "POST", "/api/complaints", token="custToken", body=complaint(), tests=f"""
{status(201)}
const c = pm.response.json();
pm.test("Open, with a reference, nobody assigned", () => {{ pm.expect(c.status).to.eql("OPEN"); pm.expect(c.number).to.match(/^CMP-\\d+$/); pm.expect(c.assigneeUsername).to.eql(null); pm.expect(c.canRate).to.be.false; }});
pm.collectionVariables.set("complaintId", c.id);
pm.collectionVariables.set("complaintNumber", c.number);
"""),
        req("File a second, low priority one", "POST", "/api/complaints", token="custToken", body=complaint("Postman Second {{runId}}", "DELIVERY", "low"), tests=f"""
{status(201)}
pm.collectionVariables.set("complaint2Id", pm.response.json().id);
"""),
        req("Agents cannot file complaints", "POST", "/api/complaints", token="agentToken", body=complaint(), tests=status(403)),
    ]),
    folder("3. Customer views only their own", [
        req("My complaints", "GET", "/api/complaints/my", token="custToken", tests=f"""
{status(200)}
pm.test("Exactly my two", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("Search by reference", "GET", "/api/complaints/my?search={{complaintNumber}}", token="custToken", tests=f"""
{status(200)}
pm.test("Found", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Another customer sees none of them", "GET", "/api/complaints/my?search={{runId}}", token="cust2Token", tests=f"""
{status(200)}
pm.test("Empty", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("Another customer cannot open mine", "GET", "/api/complaints/{{complaintId}}", token="cust2Token", tests=status(403)),
        req("Open my own, with its timeline", "GET", "/api/complaints/{{complaintId}}", token="custToken", tests=f"""
{status(200)}
pm.test("One event: filed", () => {{ const d = pm.response.json(); pm.expect(d.timeline.length).to.eql(1); pm.expect(d.timeline[0].type).to.eql("FILED"); }});
"""),
        req("Unknown complaint is 404", "GET", "/api/complaints/999999", token="agentToken", tests=status(404)),
    ]),
    folder("4. Agent queue and assignment", [
        req("The unassigned queue, most urgent first", "GET", "/api/agent/complaints?assignee=unassigned&search={{runId}}", token="agentToken", tests=f"""
{status(200)}
pm.test("High before low", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].priority).to.eql("HIGH"); }});
"""),
        req("Filter by priority", "GET", "/api/agent/complaints?priority=LOW&search={{runId}}", token="agentToken", tests=f"""
{status(200)}
pm.test("Only the low one", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Filter by category", "GET", "/api/agent/complaints?category=DELIVERY&search={{runId}}", token="agentToken", tests=f"""
{status(200)}
pm.test("Only the delivery one", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Cannot assign to a customer", "PUT", "/api/agent/complaints/{{complaintId}}/assign", token="agentToken", body={"assignee": "diya.patel"}, tests=f"""
{status(400)}
pm.test("Assignee error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("assignee"));
"""),
        req("Assign to the second agent", "PUT", "/api/agent/complaints/{{complaintId}}/assign", token="agentToken", body={"assignee": "kiran.bhat"}, tests=f"""
{status(200)}
pm.test("Assigned, still open", () => {{ const c = pm.response.json(); pm.expect(c.assigneeUsername).to.eql("kiran.bhat"); pm.expect(c.status).to.eql("OPEN"); }});
"""),
        req("Put it back in the queue", "PUT", "/api/agent/complaints/{{complaintId}}/assign", token="agentToken", body={"assignee": ""}, tests=f"""
{status(200)}
pm.test("Unassigned", () => pm.expect(pm.response.json().assigneeUsername).to.eql(null));
"""),
        req("Customers cannot assign", "PUT", "/api/agent/complaints/{{complaintId}}/assign", token="custToken", body={"assignee": "agent"}, tests=status(403)),
    ]),
    folder("5. Status flow, resolution and feedback", [
        req("Skipping to resolved is a conflict", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "RESOLVED", "note": "x"}, tests=f"""
{status(409)}
pm.test("Says in progress", () => pm.expect(pm.response.json().message).to.include("in progress"));
"""),
        req("Unknown status is rejected", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "DONE"}, tests=status(400)),
        req("Start work (takes it)", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "IN_PROGRESS", "note": "Checking the transaction"}, tests=f"""
{status(200)}
pm.test("In progress and owned by the agent", () => {{ const c = pm.response.json(); pm.expect(c.status).to.eql("IN_PROGRESS"); pm.expect(c.assigneeUsername).to.eql("agent"); }});
"""),
        req("Starting it again is a conflict", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agent2Token", body={"status": "IN_PROGRESS"}, tests=status(409)),
        req("Work in progress cannot be left without an owner", "PUT", "/api/agent/complaints/{{complaintId}}/assign", token="agentToken", body={"assignee": ""}, tests=status(400)),
        req("Resolution notes are mandatory", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "RESOLVED"}, tests=f"""
{status(400)}
pm.test("Note error", () => pm.expect(pm.response.json().fieldErrors.note).to.include("Resolution notes"));
"""),
        req("Blank notes do not count", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "RESOLVED", "note": "   "}, tests=status(400)),
        req("Rating before it is resolved is a conflict", "POST", "/api/complaints/{{complaintId}}/feedback", token="custToken", body={"rating": 5}, tests=status(409)),
        req("Resolve with notes", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "RESOLVED", "note": "Duplicate charge reversed"}, tests=f"""
{status(200)}
pm.test("Resolved, rating now possible", () => {{ const c = pm.response.json(); pm.expect(c.status).to.eql("RESOLVED"); pm.expect(c.resolution).to.eql("Duplicate charge reversed"); pm.expect(c.canRate).to.be.true; }});
"""),
        req("The customer reads the resolution", "GET", "/api/complaints/my?search={{complaintNumber}}", token="custToken", tests=f"""
{status(200)}
pm.test("Visible", () => pm.expect(pm.response.json()[0].resolution).to.eql("Duplicate charge reversed"));
"""),
        req("Rating must be 1 to 5", "POST", "/api/complaints/{{complaintId}}/feedback", token="custToken", body={"rating": 9}, tests=status(400)),
        req("Rating is required", "POST", "/api/complaints/{{complaintId}}/feedback", token="custToken", body={}, tests=f"""
{status(400)}
pm.test("Rating error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("rating"));
"""),
        req("Another customer cannot rate it", "POST", "/api/complaints/{{complaintId}}/feedback", token="cust2Token", body={"rating": 1}, tests=status(403)),
        req("The customer rates it", "POST", "/api/complaints/{{complaintId}}/feedback", token="custToken", body={"rating": 4, "comment": "Quick fix"}, tests=f"""
{status(200)}
pm.test("Rated, no longer ratable", () => {{ const c = pm.response.json(); pm.expect(c.rating).to.eql(4); pm.expect(c.feedback).to.eql("Quick fix"); pm.expect(c.canRate).to.be.false; }});
"""),
        req("Rating twice is a conflict", "POST", "/api/complaints/{{complaintId}}/feedback", token="custToken", body={"rating": 5}, tests=status(409)),
        req("A resolved complaint can go back to in progress", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "IN_PROGRESS", "note": "Customer says it recurred"}, tests=f"""
{status(200)}
pm.test("Resolved time cleared", () => pm.expect(pm.response.json().resolvedAt).to.eql(null));
"""),
        req("Resolve again", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "RESOLVED", "note": "Refund confirmed by the bank"}, tests=status(200)),
        req("Close it", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "CLOSED"}, tests=f"""
{status(200)}
pm.test("Closed, nothing further", () => {{ const c = pm.response.json(); pm.expect(c.status).to.eql("CLOSED"); pm.expect(c.allowedNext).to.eql([]); }});
"""),
        req("A closed complaint cannot be reopened", "PUT", "/api/agent/complaints/{{complaintId}}/status", token="agentToken", body={"status": "IN_PROGRESS"}, tests=f"""
{status(409)}
pm.test("Says so", () => pm.expect(pm.response.json().message).to.include("cannot be reopened"));
"""),
        req("A closed complaint cannot be reassigned", "PUT", "/api/agent/complaints/{{complaintId}}/assign", token="agentToken", body={"assignee": "kiran.bhat"}, tests=status(409)),
    ]),
    folder("6. Timeline and statistics", [
        req("The timeline tells the whole story", "GET", "/api/complaints/{{complaintId}}", token="custToken", tests=f"""
{status(200)}
pm.test("Filed, assigned, status changes and feedback", () => {{
  const t = pm.response.json().timeline;
  pm.expect(t[0].type).to.eql("FILED");
  pm.expect(t.filter(e => e.type == "STATUS_CHANGED").map(e => e.status)).to.eql(["IN_PROGRESS", "RESOLVED", "IN_PROGRESS", "RESOLVED", "CLOSED"]);
  pm.expect(t.some(e => e.type == "FEEDBACK")).to.be.true;
}});
"""),
        req("Statistics", "GET", "/api/agent/statistics", token="agentToken", tests=f"""
{status(200)}
pm.test("Counts, rating, categories and the week", () => {{
  const s = pm.response.json();
  pm.expect(s).to.include.keys("total", "open", "inProgress", "resolved", "closed", "unassigned", "mine", "waitingOverThreeDays", "resolutionRatePercent", "averageResolutionHours", "averageRating", "ratings", "byCategory", "activeByPriority", "lastSevenDays");
  pm.expect(s.byCategory.length).to.eql(6);
  pm.expect(s.lastSevenDays.length).to.eql(7);
  pm.expect(s.ratings).to.be.at.least(1);
}});
"""),
        req("The agents list shows their load", "GET", "/api/agent/agents", token="agentToken", tests=f"""
{status(200)}
pm.test("At least two agents", () => pm.expect(pm.response.json().length).to.be.at.least(2));
"""),
    ]),
    folder("7. Security checks", [
        req("No token is 401", "GET", "/api/complaints/my", tests=status(401)),
        req("Customer cannot see the queue", "GET", "/api/agent/complaints", token="custToken", tests=status(403)),
        req("Customer cannot resolve", "PUT", "/api/agent/complaints/{{complaint2Id}}/status", token="custToken", body={"status": "RESOLVED", "note": "x"}, tests=status(403)),
        req("Customer cannot see statistics", "GET", "/api/agent/statistics", token="custToken", tests=status(403)),
        req("Agent has no personal complaint list", "GET", "/api/complaints/my", token="agentToken", tests=status(403)),
        req("Agent cannot rate", "POST", "/api/complaints/{{complaintId}}/feedback", token="agentToken", body={"rating": 5}, tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Customer Complaint Management API",
    "Walks the complaint workflow: filing, own-only visibility, the priority-ordered queue, assignment, the one-step status flow (closed is final), mandatory resolution notes, customer feedback, the timeline, statistics and security.\n"
    "Start the backend first (http://localhost:8117). Repeatable: it registers a fresh customer and creates uniquely titled complaints each run.",
    items, {"baseUrl": "http://localhost:8117", **{k: "" for k in [
        "agentToken", "agent2Token", "custToken", "cust2Token", "complaintId", "complaint2Id", "complaintNumber", "runId", "newUser"]}})
