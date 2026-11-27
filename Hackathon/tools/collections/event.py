"""python tools/collections/event.py event-registration/docs/postman/Event.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
const iso = (days, hours) => new Date(Date.now() + days * 864e5 + hours * 36e5).toISOString().slice(0, 16);
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("startsAt", iso(5, 0));
pm.collectionVariables.set("endsAt", iso(5, 2));
pm.collectionVariables.set("pastStart", iso(-2, 0));
pm.collectionVariables.set("pastEnd", iso(-2, 2));
"""


def event(title="Postman Meetup {{runId}}", category="Workshop", capacity=1, starts="{{startsAt}}", ends="{{endsAt}}"):
    return {"title": title, "category": category, "venue": "Room 42", "description": "Created by the Postman run",
            "startsAt": starts, "endsAt": ends, "capacity": capacity}


def register(event_id="{{eventId}}", waitlist=False):
    return {"eventId": event_id, "joinWaitlist": waitlist}


items = [
    folder("1. Auth", [
        req("Login as event admin", "POST", "/api/auth/login", body={"username": "eventadmin", "password": "admin123"},
            tests=login_tests("adminToken"), pre=PRE),
        req("Register a fresh employee for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Employee", "role": "EVENT_ADMIN"}, tests=f"""
{status(201)}
pm.test("Always an employee", () => pm.expect(pm.response.json().role).to.eql("EMPLOYEE"));
pm.collectionVariables.set("emp1Token", pm.response.json().token);
"""),
        req("Login as a second employee", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "employee123"},
            tests=login_tests("emp2Token")),
        req("Login as a third employee", "POST", "/api/auth/login", body={"username": "neha.kapoor", "password": "employee123"},
            tests=login_tests("emp3Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "eventadmin", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Admin creates and publishes an event", [
        req("Create an event with one seat (starts as a draft)", "POST", "/api/admin/events", token="adminToken", body=event(), tests=f"""
{status(201)}
const e = pm.response.json();
pm.test("Draft with nobody registered", () => {{ pm.expect(e.status).to.eql("DRAFT"); pm.expect(e.confirmed).to.eql(0); pm.expect(e.spotsLeft).to.eql(1); }});
pm.collectionVariables.set("eventId", e.id);
"""),
        req("Empty form lists every problem", "POST", "/api/admin/events", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("title", "category", "venue", "description", "startsAt", "endsAt", "capacity"));
"""),
        req("An event in the past cannot be created", "POST", "/api/admin/events", token="adminToken", body=event(starts="{{pastStart}}", ends="{{pastEnd}}"), tests=f"""
{status(400)}
pm.test("Start time error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("startsAt"));
"""),
        req("End before start is rejected", "POST", "/api/admin/events", token="adminToken", body=event(ends="{{pastEnd}}"), tests=f"""
{status(400)}
pm.test("End time error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("endsAt"));
"""),
        req("Unknown category is rejected", "POST", "/api/admin/events", token="adminToken", body=event(category="Nonsense"), tests=f"""
{status(400)}
pm.test("Category error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("category"));
"""),
        req("Capacity must be positive", "POST", "/api/admin/events", token="adminToken", body=event(capacity=0), tests=f"""
{status(400)}
pm.test("Capacity error", () => pm.expect(pm.response.json().fieldErrors.capacity).to.include("at least 1"));
"""),
        req("A draft is invisible to employees", "GET", "/api/events?search={{runId}}", token="emp1Token", tests=f"""
{status(200)}
pm.test("Not listed", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("A draft takes no registrations", "POST", "/api/registrations", token="emp1Token", body=register(), tests=f"""
{status(400)}
pm.test("Says it is not open", () => pm.expect(pm.response.json().message).to.include("not open"));
"""),
        req("Publish", "PUT", "/api/admin/events/{{eventId}}/publish", token="adminToken", tests=f"""
{status(200)}
pm.test("Published", () => pm.expect(pm.response.json().status).to.eql("PUBLISHED"));
"""),
        req("Publishing again is a conflict", "PUT", "/api/admin/events/{{eventId}}/publish", token="adminToken", tests=status(409)),
        req("Employees now find it", "GET", "/api/events?search={{runId}}&category=Workshop", token="emp1Token", tests=f"""
{status(200)}
pm.test("Found with its single seat", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].spotsLeft).to.eql(1); pm.expect(l[0].myStatus).to.eql(null); }});
"""),
        req("A different category filter hides it", "GET", "/api/events?search={{runId}}&category=Sports", token="emp1Token", tests=f"""
{status(200)}
pm.test("Hidden", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
    ]),
    folder("3. Employees register: capacity and waitlist", [
        req("Employee 1 takes the only seat", "POST", "/api/registrations", token="emp1Token", body=register(), tests=f"""
{status(201)}
pm.test("Confirmed", () => pm.expect(pm.response.json().status).to.eql("CONFIRMED"));
pm.collectionVariables.set("reg1Id", pm.response.json().id);
"""),
        req("Registering twice is a conflict", "POST", "/api/registrations", token="emp1Token", body=register(), tests=f"""
{status(409)}
pm.test("Explains", () => pm.expect(pm.response.json().message).to.include("already registered"));
"""),
        req("A full event refuses employee 2", "POST", "/api/registrations", token="emp2Token", body=register(), tests=f"""
{status(409)}
pm.test("Mentions the waitlist", () => pm.expect(pm.response.json().message).to.include("full").and.to.include("waitlist"));
"""),
        req("Employee 2 joins the waitlist", "POST", "/api/registrations", token="emp2Token", body=register(waitlist=True), tests=f"""
{status(201)}
const r = pm.response.json();
pm.test("Waitlisted at position 1", () => {{ pm.expect(r.status).to.eql("WAITLISTED"); pm.expect(r.waitlistPosition).to.eql(1); }});
pm.collectionVariables.set("reg2Id", r.id);
"""),
        req("Employee 3 joins the waitlist", "POST", "/api/registrations", token="emp3Token", body=register(waitlist=True), tests=f"""
{status(201)}
pm.test("Position 2", () => pm.expect(pm.response.json().waitlistPosition).to.eql(2));
"""),
        req("Joining the waitlist twice is a conflict", "POST", "/api/registrations", token="emp3Token", body=register(waitlist=True), tests=status(409)),
        req("Missing event is reported", "POST", "/api/registrations", token="emp1Token", body={}, tests=f"""
{status(400)}
pm.test("Field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("eventId"));
"""),
        req("Unknown event is reported", "POST", "/api/registrations", token="emp1Token", body=register(event_id=999999), tests=f"""
{status(400)}
pm.test("Field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("eventId"));
"""),
    ]),
    folder("4. Employee views", [
        req("My registrations", "GET", "/api/registrations/my", token="emp1Token", tests=f"""
{status(200)}
pm.test("Exactly my one", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].status).to.eql("CONFIRMED"); }});
"""),
        req("Employee 3 sees the waitlist position", "GET", "/api/registrations/my", token="emp3Token", tests=f"""
{status(200)}
pm.test("Position 2 on this event", () => {{ const r = pm.response.json().find(x => x.eventId == pm.collectionVariables.get("eventId")); pm.expect(r.waitlistPosition).to.eql(2); }});
"""),
        req("The event list shows seats and my status", "GET", "/api/events?search={{runId}}", token="emp2Token", tests=f"""
{status(200)}
pm.test("Full, one waiting two, I am waitlisted", () => {{ const e = pm.response.json()[0]; pm.expect(e.spotsLeft).to.eql(0); pm.expect(e.waitlisted).to.eql(2); pm.expect(e.myStatus).to.eql("WAITLISTED"); }});
"""),
    ]),
    folder("5. Cancelling promotes the waitlist", [
        req("Employee 2 cannot cancel employee 1's registration", "DELETE", "/api/registrations/{{reg1Id}}", token="emp2Token", tests=status(403)),
        req("Unknown registration is 404", "DELETE", "/api/registrations/999999", token="emp1Token", tests=status(404)),
        req("Employee 1 cancels", "DELETE", "/api/registrations/{{reg1Id}}", token="emp1Token", tests=f"""
{status(200)}
pm.test("Cancelled", () => pm.expect(pm.response.json().status).to.eql("CANCELLED"));
"""),
        req("Cancelling again is a conflict", "DELETE", "/api/registrations/{{reg1Id}}", token="emp1Token", tests=status(409)),
        req("Employee 2 was promoted automatically", "GET", "/api/registrations/my", token="emp2Token", tests=f"""
{status(200)}
pm.test("Confirmed now", () => {{ const r = pm.response.json().find(x => x.id == pm.collectionVariables.get("reg2Id")); pm.expect(r.status).to.eql("CONFIRMED"); }});
"""),
        req("Employee 3 moved up to position 1", "GET", "/api/registrations/my", token="emp3Token", tests=f"""
{status(200)}
pm.test("First in line", () => {{ const r = pm.response.json().find(x => x.eventId == pm.collectionVariables.get("eventId")); pm.expect(r.waitlistPosition).to.eql(1); }});
"""),
        req("Employee 1 can register again (joins the waitlist, event is full)", "POST", "/api/registrations", token="emp1Token", body=register(waitlist=True), tests=f"""
{status(201)}
pm.test("Waitlisted behind employee 3", () => {{ const r = pm.response.json(); pm.expect(r.status).to.eql("WAITLISTED"); pm.expect(r.waitlistPosition).to.eql(2); }});
"""),
    ]),
    folder("6. Admin manages participants, capacity and cancellation", [
        req("Participants: confirmed first, then the waitlist in order", "GET", "/api/admin/events/{{eventId}}/participants", token="adminToken", tests=f"""
{status(200)}
pm.test("One confirmed, two waiting", () => {{
  const l = pm.response.json();
  pm.expect(l.map(p => p.status)).to.eql(["CONFIRMED", "WAITLISTED", "WAITLISTED"]);
  pm.expect(l.map(p => p.waitlistPosition)).to.eql([null, 1, 2]);
}});
"""),
        req("Unknown event participants is 404", "GET", "/api/admin/events/999999/participants", token="adminToken", tests=status(404)),
        req("Editing an event keeps its data", "PUT", "/api/admin/events/{{eventId}}", token="adminToken", body=event(capacity=1, title="Postman Meetup {{runId}}"), tests=status(200)),
        req("Raising capacity to 3 promotes the whole waitlist", "PUT", "/api/admin/events/{{eventId}}", token="adminToken", body=event(capacity=3), tests=f"""
{status(200)}
pm.test("Three confirmed, nobody waiting", () => {{ const e = pm.response.json(); pm.expect(e.confirmed).to.eql(3); pm.expect(e.waitlisted).to.eql(0); }});
"""),
        req("Capacity below confirmed is rejected", "PUT", "/api/admin/events/{{eventId}}", token="adminToken", body=event(capacity=2), tests=f"""
{status(400)}
pm.test("Capacity error", () => pm.expect(pm.response.json().fieldErrors.capacity).to.include("already confirmed"));
"""),
        req("Dashboard", "GET", "/api/admin/dashboard", token="adminToken", tests=f"""
{status(200)}
pm.test("Totals and per-event rows", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("totalEvents", "upcomingPublished", "confirmedSeats", "waitlisted", "events");
  const e = d.events.find(x => x.id == pm.collectionVariables.get("eventId"));
  pm.expect(e.confirmed).to.eql(3); pm.expect(e.fillPercent).to.eql(100);
}});
"""),
        req("Admin sees all events including drafts", "GET", "/api/admin/events?status=PUBLISHED", token="adminToken", tests=f"""
{status(200)}
pm.test("Contains this run's event", () => pm.expect(pm.response.json().some(e => e.id == pm.collectionVariables.get("eventId"))).to.be.true);
"""),
        req("Cancel the event", "PUT", "/api/admin/events/{{eventId}}/cancel", token="adminToken", tests=f"""
{status(200)}
pm.test("Cancelled", () => pm.expect(pm.response.json().status).to.eql("CANCELLED"));
"""),
        req("Cancelling again is a conflict", "PUT", "/api/admin/events/{{eventId}}/cancel", token="adminToken", tests=status(409)),
        req("A cancelled event cannot be edited", "PUT", "/api/admin/events/{{eventId}}", token="adminToken", body=event(), tests=status(409)),
        req("A cancelled event takes no registrations", "POST", "/api/registrations", token="emp1Token", body=register(), tests=status(400)),
        req("Its registrations were released", "GET", "/api/admin/events/{{eventId}}/participants", token="adminToken", tests=f"""
{status(200)}
pm.test("Nobody left", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("Employees no longer see it", "GET", "/api/events?search={{runId}}", token="emp1Token", tests=f"""
{status(200)}
pm.test("Hidden", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
    ]),
    folder("7. Security checks", [
        req("No token is 401", "GET", "/api/events", tests=status(401)),
        req("Employee cannot list admin events", "GET", "/api/admin/events", token="emp1Token", tests=status(403)),
        req("Employee cannot create events", "POST", "/api/admin/events", token="emp1Token", body=event(), tests=status(403)),
        req("Employee cannot see the dashboard", "GET", "/api/admin/dashboard", token="emp1Token", tests=status(403)),
        req("Employee cannot cancel an event", "PUT", "/api/admin/events/{{eventId}}/cancel", token="emp1Token", tests=status(403)),
        req("Admin cannot register for events", "POST", "/api/registrations", token="adminToken", body=register(), tests=status(403)),
        req("Admin has no personal registrations", "GET", "/api/registrations/my", token="adminToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Event Registration API",
    "Walks the event workflow: draft and publish, registration, capacity, the waitlist and automatic promotion, admin participant lists, the dashboard and security.\n"
    "Start the backend first (http://localhost:8118). Repeatable: it registers a fresh employee and creates a uniquely named event each run.",
    items, {"baseUrl": "http://localhost:8118", **{k: "" for k in [
        "adminToken", "emp1Token", "emp2Token", "emp3Token", "eventId", "reg1Id", "reg2Id",
        "runId", "newUser", "startsAt", "endsAt", "pastStart", "pastEnd"]}})
