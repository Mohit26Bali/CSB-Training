"""python tools/collections/appointment.py appointment-booking/docs/postman/Appointment.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
const day = (offset) => { const d = new Date(Date.now() + offset * 864e5); return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0"); };
const base = 20 + Math.floor(Math.random() * 40);
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("day", day(base));
pm.collectionVariables.set("yesterday", day(-1));
pm.collectionVariables.set("tomorrow", day(1));
"""


def slot(time="07:00", minutes=30, date="{{day}}"):
    return {"date": date, "startTime": time, "minutes": minutes}


def bulk(from_time="08:00", to_time="10:00", minutes=30, date="{{day}}"):
    return {"date": date, "fromTime": from_time, "toTime": to_time, "minutes": minutes}


MEHTA = {"title": "General physician", "description": "Consultations for fever, infections, check-ups and prescriptions.", "location": "Clinic 2, MG Road, Pune"}

items = [
    folder("1. Auth", [
        req("Login as the physician", "POST", "/api/auth/login", body={"username": "dr.mehta", "password": "provider123"},
            tests=login_tests("mehtaToken"), pre=PRE),
        req("Login as the dentist", "POST", "/api/auth/login", body={"username": "dr.rao", "password": "provider123"},
            tests=login_tests("raoToken")),
        req("Register a fresh customer for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Customer", "role": "SERVICE_PROVIDER"}, tests=f"""
{status(201)}
pm.test("Always a customer", () => pm.expect(pm.response.json().role).to.eql("CUSTOMER"));
pm.collectionVariables.set("customerToken", pm.response.json().token);
"""),
        req("Login as another customer", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "customer123"},
            tests=login_tests("otherToken")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "dr.mehta", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. The provider creates slots", [
        req("Empty form lists every problem", "POST", "/api/provider/slots", token="mehtaToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("date", "startTime", "minutes"));
"""),
        req("A slot in the past is rejected", "POST", "/api/provider/slots", token="mehtaToken", body=slot(date="{{yesterday}}"), tests=f"""
{status(400)}
pm.test("Date error", () => pm.expect(pm.response.json().fieldErrors.date).to.include("future"));
"""),
        req("A five minute slot is rejected", "POST", "/api/provider/slots", token="mehtaToken", body=slot(minutes=5), tests=f"""
{status(400)}
pm.test("Minutes error", () => pm.expect(pm.response.json().fieldErrors.minutes).to.include("15"));
"""),
        req("A slot running past midnight is rejected", "POST", "/api/provider/slots", token="mehtaToken", body=slot("23:30", 60), tests=f"""
{status(400)}
pm.test("Same-day error", () => pm.expect(pm.response.json().fieldErrors.minutes).to.include("same day"));
"""),
        req("Create the first slot", "POST", "/api/provider/slots", token="mehtaToken", body=slot("07:00"), tests=f"""
{status(201)}
pm.test("Available, 30 minutes", () => {{ pm.expect(pm.response.json().status).to.eql("AVAILABLE"); pm.expect(pm.response.json().minutes).to.eql(30); }});
pm.collectionVariables.set("slot1", pm.response.json().id);
"""),
        req("An overlapping slot is a conflict", "POST", "/api/provider/slots", token="mehtaToken", body=slot("07:15"), tests=f"""
{status(409)}
pm.test("Says so", () => pm.expect(pm.response.json().message).to.include("overlaps"));
"""),
        req("A slot that starts when the first ends is fine", "POST", "/api/provider/slots", token="mehtaToken", body=slot("07:30"), tests=f"""
{status(201)}
pm.collectionVariables.set("slot2", pm.response.json().id);
"""),
        req("Bulk: end before start is rejected", "POST", "/api/provider/slots/bulk", token="mehtaToken", body=bulk("10:00", "09:00"), tests=f"""
{status(400)}
pm.test("To-time error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("toTime"));
"""),
        req("Bulk: far too many slots is rejected", "POST", "/api/provider/slots/bulk", token="mehtaToken", body=bulk("00:00", "23:00", 15), tests=f"""
{status(400)}
pm.test("Limit mentioned", () => pm.expect(pm.response.json().fieldErrors.minutes).to.include("at most 24"));
"""),
        req("Bulk: cut two hours into four slots", "POST", "/api/provider/slots/bulk", token="mehtaToken", body=bulk(), tests=f"""
{status(201)}
const list = pm.response.json();
pm.test("Four consecutive slots", () => {{ pm.expect(list.length).to.eql(4); pm.expect(list[0].end).to.eql(list[1].start); }});
pm.collectionVariables.set("a1", list[0].id);
pm.collectionVariables.set("a2", list[1].id);
pm.collectionVariables.set("a3", list[2].id);
"""),
        req("Bulk over existing slots is all or nothing", "POST", "/api/provider/slots/bulk", token="mehtaToken", body=bulk("07:00", "08:30"), tests=status(409)),
        req("The calendar holds exactly six slots", "GET", "/api/provider/slots?from={{day}}&days=1", token="mehtaToken", tests=f"""
{status(200)}
pm.test("Six, in time order", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(6); pm.expect(l.every(s => s.status === "AVAILABLE")).to.be.true; pm.expect(l[0].start < l[5].start).to.be.true; }});
"""),
        req("The dentist adds an overlapping time of their own", "POST", "/api/provider/slots", token="raoToken", body=slot("07:10"), tests=f"""
{status(201)}
pm.collectionVariables.set("raoSlot", pm.response.json().id);
"""),
        req("Customers cannot create slots", "POST", "/api/provider/slots", token="customerToken", body=slot("11:00"), tests=status(403)),
    ]),
    folder("3. Customers browse", [
        req("Providers with open slots", "GET", "/api/providers", token="customerToken", tests=f"""
{status(200)}
const list = pm.response.json();
pm.test("Five providers, the physician has open slots", () => {{ pm.expect(list.length).to.be.at.least(5); const m = list.find(p => p.title === "General physician"); pm.expect(m.freeSlots).to.be.above(0); pm.collectionVariables.set("mehtaId", m.id); }});
"""),
        req("Search by speciality", "GET", "/api/providers?search=dentist", token="customerToken", tests=f"""
{status(200)}
pm.test("Just the dentist", () => {{ pm.expect(pm.response.json().length).to.eql(1); pm.expect(pm.response.json()[0].title).to.eql("Dentist"); }});
"""),
        req("The physician's slots on the day", "GET", "/api/providers/{{mehtaId}}/slots?from={{day}}&days=1", token="customerToken", tests=f"""
{status(200)}
pm.test("Six open slots, no personal details", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(6); pm.expect(l.every(s => s.status === "AVAILABLE" && s.customerName === null)).to.be.true; }});
"""),
        req("Too many days is rejected", "GET", "/api/providers/{{mehtaId}}/slots?days=99", token="customerToken", tests=status(400)),
        req("Unknown provider is 404", "GET", "/api/providers/999999/slots", token="customerToken", tests=status(404)),
        req("The provider's own calendar needs the provider role", "GET", "/api/provider/slots", token="customerToken", tests=status(403)),
    ]),
    folder("4. Booking", [
        req("Empty booking lists the problem", "POST", "/api/appointments", token="customerToken", body={}, tests=f"""
{status(400)}
pm.test("Slot error", () => pm.expect(pm.response.json().fieldErrors.slotId).to.eql("Choose a slot"));
"""),
        req("Book the first slot", "POST", "/api/appointments", token="customerToken", body={"slotId": "{{slot1}}", "reason": "Annual check-up"}, tests=f"""
{status(201)}
const a = pm.response.json();
pm.test("Pending with the provider's details", () => {{ pm.expect(a.status).to.eql("PENDING"); pm.expect(a.providerName).to.include("Mehta"); pm.expect(a.number).to.match(/^AP-\\d+$/); pm.expect(a.canCancel).to.be.true; pm.expect(a.canConfirm).to.be.true; }});
pm.collectionVariables.set("appt1", a.id);
"""),
        req("Someone else cannot take the same slot", "POST", "/api/appointments", token="otherToken", body={"slotId": "{{slot1}}"}, tests=f"""
{status(409)}
pm.test("Just taken", () => pm.expect(pm.response.json().message).to.include("just been taken"));
"""),
        req("The same customer cannot be in two places at once", "POST", "/api/appointments", token="customerToken", body={"slotId": "{{raoSlot}}"}, tests=f"""
{status(409)}
pm.test("Already booked then", () => pm.expect(pm.response.json().message).to.include("already have an appointment"));
"""),
        req("A back-to-back slot is fine", "POST", "/api/appointments", token="customerToken", body={"slotId": "{{a1}}", "reason": "Follow-up"}, tests=f"""
{status(201)}
pm.collectionVariables.set("appt2", pm.response.json().id);
"""),
        req("Providers cannot book", "POST", "/api/appointments", token="mehtaToken", body={"slotId": "{{a2}}"}, tests=status(403)),
        req("Upcoming appointments", "GET", "/api/appointments/my?scope=upcoming", token="customerToken", tests=f"""
{status(200)}
pm.test("Both, soonest first", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].start < l[1].start).to.be.true; }});
"""),
        req("Previous appointments are empty", "GET", "/api/appointments/my?scope=previous", token="customerToken", tests=f"""
{status(200)}
pm.test("None yet", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("A bad scope is rejected", "GET", "/api/appointments/my?scope=soon", token="customerToken", tests=status(400)),
        req("The first slot now shows as taken, without the customer", "GET", "/api/providers/{{mehtaId}}/slots?from={{day}}&days=1", token="otherToken", tests=f"""
{status(200)}
pm.test("Two taken, four open", () => {{ const l = pm.response.json(); pm.expect(l.filter(s => s.status === "PENDING").length).to.eql(2); pm.expect(l.filter(s => s.status === "AVAILABLE").length).to.eql(4); pm.expect(l.every(s => s.customerName === null)).to.be.true; }});
"""),
    ]),
    folder("5. The provider decides", [
        req("Customers cannot confirm", "PUT", "/api/provider/appointments/{{appt1}}/confirm", token="customerToken", tests=status(403)),
        req("Another provider cannot confirm", "PUT", "/api/provider/appointments/{{appt1}}/confirm", token="raoToken", tests=status(403)),
        req("The provider sees requests, pending first", "GET", "/api/provider/appointments?search={{newUser}}", token="mehtaToken", tests=f"""
{status(200)}
pm.test("Both of this customer's requests", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l.every(a => a.customerUsername === pm.collectionVariables.get("newUser"))).to.be.true; }});
"""),
        req("Confirm the first", "PUT", "/api/provider/appointments/{{appt1}}/confirm", token="mehtaToken", tests=f"""
{status(200)}
pm.test("Confirmed by the provider", () => {{ const a = pm.response.json(); pm.expect(a.status).to.eql("CONFIRMED"); pm.expect(a.confirmedBy).to.eql("dr.mehta"); pm.expect(a.canConfirm).to.be.false; }});
"""),
        req("Confirming twice is a conflict", "PUT", "/api/provider/appointments/{{appt1}}/confirm", token="mehtaToken", tests=status(409)),
        req("The provider must give a reason to cancel", "PUT", "/api/provider/appointments/{{appt2}}/cancel", token="mehtaToken", body={}, tests=f"""
{status(400)}
pm.test("Reason error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("reason"));
"""),
        req("The provider cancels the second request", "PUT", "/api/provider/appointments/{{appt2}}/cancel", token="mehtaToken", body={"reason": "Called away to an emergency"}, tests=f"""
{status(200)}
pm.test("Cancelled by the provider", () => {{ const a = pm.response.json(); pm.expect(a.status).to.eql("CANCELLED"); pm.expect(a.cancelledBy).to.eql("dr.mehta"); }});
"""),
        req("A cancelled request cannot be confirmed", "PUT", "/api/provider/appointments/{{appt2}}/confirm", token="mehtaToken", tests=status(409)),
        req("The customer sees the provider's reason", "GET", "/api/appointments/my?scope=previous", token="customerToken", tests=f"""
{status(200)}
pm.test("Reason shown", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].cancelReason).to.eql("Called away to an emergency"); }});
"""),
        req("The freed slot can be booked by someone else", "POST", "/api/appointments", token="otherToken", body={"slotId": "{{a1}}", "reason": "Cough"}, tests=f"""
{status(201)}
pm.collectionVariables.set("appt3", pm.response.json().id);
"""),
        req("Filter by status and day", "GET", "/api/provider/appointments?status=CONFIRMED&date={{day}}", token="mehtaToken", tests=f"""
{status(200)}
pm.test("The confirmed one", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].number).to.match(/^AP-/); }});
"""),
        req("Dashboard", "GET", "/api/provider/dashboard", token="mehtaToken", tests=f"""
{status(200)}
pm.test("Week summary", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("todayAppointments", "pending", "upcomingConfirmed", "freeSlotsWeek", "bookedSlotsWeek", "utilisationPercent", "days", "next");
  pm.expect(d.days.length).to.eql(7);
  pm.expect(d.upcomingConfirmed).to.be.at.least(1);
}});
"""),
    ]),
    folder("6. Cancelling and withdrawing", [
        req("Another customer cannot cancel it", "DELETE", "/api/appointments/{{appt1}}", token="otherToken", tests=status(403)),
        req("The customer cancels with a reason", "DELETE", "/api/appointments/{{appt1}}?reason=Feeling%20better", token="customerToken", tests=f"""
{status(200)}
pm.test("Cancelled by the customer", () => {{ const a = pm.response.json(); pm.expect(a.status).to.eql("CANCELLED"); pm.expect(a.cancelReason).to.eql("Feeling better"); pm.expect(a.cancelledBy).to.eql(pm.collectionVariables.get("newUser")); }});
"""),
        req("Cancelling twice is a conflict", "DELETE", "/api/appointments/{{appt1}}", token="customerToken", tests=status(409)),
        req("The other customer cancels theirs", "DELETE", "/api/appointments/{{appt3}}", token="otherToken", tests=status(200)),
        req("Unknown appointment is 404", "DELETE", "/api/appointments/999999", token="customerToken", tests=status(404)),
        req("Book a slot, then try to withdraw it", "POST", "/api/appointments", token="customerToken", body={"slotId": "{{a2}}"}, tests=f"""
{status(201)}
pm.collectionVariables.set("appt4", pm.response.json().id);
"""),
        req("A slot with an appointment cannot be withdrawn", "DELETE", "/api/provider/slots/{{a2}}", token="mehtaToken", tests=f"""
{status(409)}
pm.test("Cancel the appointment first", () => pm.expect(pm.response.json().message).to.include("Cancel the appointment"));
"""),
        req("Another provider cannot withdraw it either", "DELETE", "/api/provider/slots/{{a3}}", token="raoToken", tests=status(403)),
        req("Withdraw an open slot", "DELETE", "/api/provider/slots/{{a3}}", token="mehtaToken", tests=status(204)),
        req("Withdrawing twice is a conflict", "DELETE", "/api/provider/slots/{{a3}}", token="mehtaToken", tests=status(409)),
        req("A withdrawn slot cannot be booked", "POST", "/api/appointments", token="otherToken", body={"slotId": "{{a3}}"}, tests=status(400)),
        req("A withdrawn slot is hidden", "GET", "/api/providers/{{mehtaId}}/slots?from={{day}}&days=1", token="otherToken", tests=f"""
{status(200)}
pm.test("Not listed", () => pm.expect(pm.response.json().some(s => s.id == pm.collectionVariables.get("a3"))).to.be.false);
"""),
        req("Cancel the last appointment", "DELETE", "/api/appointments/{{appt4}}", token="customerToken", tests=status(200)),
    ]),
    folder("7. Profile", [
        req("A blank speciality is rejected", "PUT", "/api/provider/profile", token="mehtaToken", body={"title": "", "description": "x"}, tests=f"""
{status(400)}
pm.test("Title error", () => pm.expect(pm.response.json().fieldErrors.title).to.eql("Title is required"));
"""),
        req("Change the profile", "PUT", "/api/provider/profile", token="mehtaToken", body={"title": "Family physician", "description": "Changed by the Postman run", "location": "Online"}, tests=f"""
{status(200)}
pm.test("Saved", () => pm.expect(pm.response.json().title).to.eql("Family physician"));
"""),
        req("Customers see it", "GET", "/api/providers?search=family", token="customerToken", tests=f"""
{status(200)}
pm.test("Found by the new title", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Put the profile back", "PUT", "/api/provider/profile", token="mehtaToken", body=MEHTA, tests=f"""
{status(200)}
pm.test("Restored", () => pm.expect(pm.response.json().title).to.eql("General physician"));
"""),
    ]),
    folder("8. Security checks", [
        req("No token is 401", "GET", "/api/appointments/my", tests=status(401)),
        req("Provider has no personal appointments", "GET", "/api/appointments/my", token="mehtaToken", tests=status(403)),
        req("Customer cannot see provider appointments", "GET", "/api/provider/appointments", token="customerToken", tests=status(403)),
        req("Customer cannot see the dashboard", "GET", "/api/provider/dashboard", token="customerToken", tests=status(403)),
        req("Customer cannot edit the profile", "PUT", "/api/provider/profile", token="customerToken", body=MEHTA, tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Appointment Booking API",
    "Walks the appointment workflow: providers create single and bulk slots, customers browse and book, double-booking and overlap protection, "
    "provider confirmation and cancellation with reasons, customer cancellation, withdrawing slots, the profile, the dashboard and security.\n"
    "Start the backend first (http://localhost:8120). Repeatable: it registers a fresh customer and creates slots 3 to 8 weeks ahead in the early morning.",
    items, {"baseUrl": "http://localhost:8120", **{k: "" for k in [
        "mehtaToken", "raoToken", "customerToken", "otherToken", "runId", "newUser", "day", "yesterday", "tomorrow",
        "slot1", "slot2", "a1", "a2", "a3", "raoSlot", "mehtaId", "appt1", "appt2", "appt3", "appt4"]}})
