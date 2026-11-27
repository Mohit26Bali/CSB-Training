"""python tools/collections/vehicle.py vehicle-service/docs/postman/Vehicle.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
const letters = () => String.fromCharCode(65 + Math.floor(Math.random() * 26)) + String.fromCharCode(65 + Math.floor(Math.random() * 26));
const digits = () => String(Math.floor(Math.random() * 9000 + 1000));
const day = (offset) => { const d = new Date(Date.now() + offset * 864e5); return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0"); };
const base = 45 + Math.floor(Math.random() * 30);
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
const car = "MH12" + letters() + digits();
pm.collectionVariables.set("carNo", car);
pm.collectionVariables.set("carNoSpaced", car.slice(0, 4).toLowerCase() + " " + car.slice(4, 6).toLowerCase() + "-" + car.slice(6));
pm.collectionVariables.set("bikeNo", "MH14" + letters() + digits());
pm.collectionVariables.set("serviceName", "Postman wash " + id);
pm.collectionVariables.set("today", day(0));
pm.collectionVariables.set("yesterday", day(-1));
pm.collectionVariables.set("far", day(95));
pm.collectionVariables.set("d1", day(base));
pm.collectionVariables.set("d2", day(base + 1));
pm.collectionVariables.set("d3", day(base + 3));
pm.collectionVariables.set("d4", day(base + 8));
"""


def vehicle(number="{{carNo}}", vtype="CAR", make="Maruti Suzuki", model="Swift", year=2020):
    return {"number": number, "type": vtype, "make": make, "model": model, "year": year}


def booking(vehicle_id="{{carId}}", date="{{d1}}", service="{{fullId}}", notes=None):
    return {"vehicleId": vehicle_id, "serviceTypeId": service, "serviceDate": date, "notes": notes}


def service(name="{{serviceName}}", days=1, price=900, active=True):
    return {"name": name, "description": "Created by the Postman run", "durationDays": days, "basePrice": price, "active": active}


items = [
    folder("1. Auth", [
        req("Login as service admin", "POST", "/api/auth/login", body={"username": "serviceadmin", "password": "admin123"},
            tests=login_tests("adminToken"), pre=PRE),
        req("Register a fresh customer for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Customer", "role": "SERVICE_ADMIN"}, tests=f"""
{status(201)}
pm.test("Always a customer", () => pm.expect(pm.response.json().role).to.eql("CUSTOMER"));
pm.collectionVariables.set("customerToken", pm.response.json().token);
"""),
        req("Login as another customer", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "customer123"},
            tests=login_tests("otherToken")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "serviceadmin", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Registering vehicles", [
        req("Empty form lists every problem", "POST", "/api/vehicles", token="customerToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("number", "type", "make", "model", "year"));
"""),
        req("A badly formed number is rejected", "POST", "/api/vehicles", token="customerToken", body=vehicle("HELLO"), tests=f"""
{status(400)}
pm.test("Format hint", () => pm.expect(pm.response.json().fieldErrors.number).to.include("MH12AB1234"));
"""),
        req("An old year is rejected", "POST", "/api/vehicles", token="customerToken", body=vehicle(year=1975), tests=f"""
{status(400)}
pm.test("Year error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("year"));
"""),
        req("Register the car", "POST", "/api/vehicles", token="customerToken", body=vehicle(), tests=f"""
{status(201)}
const v = pm.response.json();
pm.test("Stored as given, no services yet", () => {{ pm.expect(v.number).to.eql(pm.collectionVariables.get("carNo")); pm.expect(v.type).to.eql("CAR"); pm.expect(v.completedServices).to.eql(0); }});
pm.collectionVariables.set("carId", v.id);
"""),
        req("The same number again is rejected", "POST", "/api/vehicles", token="customerToken", body=vehicle(), tests=f"""
{status(400)}
pm.test("Already registered", () => pm.expect(pm.response.json().fieldErrors.number).to.include("already registered"));
"""),
        req("Another customer cannot take the number (spaces and case ignored)", "POST", "/api/vehicles", token="otherToken",
            body=vehicle("{{carNoSpaced}}"), tests=status(400)),
        req("Register the bike", "POST", "/api/vehicles", token="customerToken", body=vehicle("{{bikeNo}}", "BIKE", "Royal Enfield", "Classic 350", 2021), tests=f"""
{status(201)}
pm.collectionVariables.set("bikeId", pm.response.json().id);
"""),
        req("My vehicles", "GET", "/api/vehicles", token="customerToken", tests=f"""
{status(200)}
pm.test("Just mine", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("The garage has no personal vehicles", "GET", "/api/vehicles", token="adminToken", tests=status(403)),
    ]),
    folder("3. Services and free days", [
        req("Services priced for a car", "GET", "/api/catalog/services?vehicleType=car", token="customerToken", tests=f"""
{status(200)}
const list = pm.response.json();
pm.test("Active services with an estimate", () => {{ pm.expect(list.length).to.be.at.least(5); list.forEach(s => {{ pm.expect(s.active).to.be.true; pm.expect(s.estimatedCost).to.eql(s.basePrice); }}); }});
const full = list.find(s => s.name === "Full service");
pm.collectionVariables.set("fullId", full.id);
pm.collectionVariables.set("basicId", list.find(s => s.name === "Basic service").id);
pm.collectionVariables.set("carFullCost", full.estimatedCost);
"""),
        req("Bikes pay less", "GET", "/api/catalog/services?vehicleType=bike", token="customerToken", tests=f"""
{status(200)}
pm.test("Cheaper than the car price", () => {{ const full = pm.response.json().find(s => s.name === "Full service"); pm.expect(full.estimatedCost).to.be.below(Number(pm.collectionVariables.get("carFullCost"))); }});
"""),
        req("An unknown vehicle type is rejected", "GET", "/api/catalog/services?vehicleType=boat", token="customerToken", tests=status(400)),
        req("Free bays for the next days", "GET", "/api/catalog/availability?days=14", token="customerToken", tests=f"""
{status(200)}
pm.test("14 days, bays add up", () => {{ const days = pm.response.json(); pm.expect(days.length).to.eql(14); days.forEach(d => pm.expect(d.booked + d.free).to.be.at.least(d.bays)); }});
"""),
        req("Asking for zero days is rejected", "GET", "/api/catalog/availability?days=0", token="customerToken", tests=f"""
{status(400)}
pm.test("Days error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("days"));
"""),
        req("No token is 401", "GET", "/api/catalog/services", tests=status(401)),
    ]),
    folder("4. Booking", [
        req("Empty booking lists the problems", "POST", "/api/bookings", token="customerToken", body={}, tests=f"""
{status(400)}
pm.test("Fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("vehicleId", "serviceTypeId", "serviceDate"));
"""),
        req("A past date is rejected", "POST", "/api/bookings", token="customerToken", body=booking(date="{{yesterday}}"), tests=f"""
{status(400)}
pm.test("Date error", () => pm.expect(pm.response.json().fieldErrors.serviceDate).to.include("past"));
"""),
        req("More than 90 days ahead is rejected", "POST", "/api/bookings", token="customerToken", body=booking(date="{{far}}"), tests=f"""
{status(400)}
pm.test("Date error", () => pm.expect(pm.response.json().fieldErrors.serviceDate).to.include("90 days"));
"""),
        req("Someone else's vehicle is forbidden", "POST", "/api/bookings", token="otherToken", body=booking(), tests=status(403)),
        req("Book a full service (two days)", "POST", "/api/bookings", token="customerToken", body=booking(notes="Strange noise from the front"), tests=f"""
{status(201)}
const b = pm.response.json();
pm.test("Booked for two days with the estimate", () => {{ pm.expect(b.status).to.eql("BOOKED"); pm.expect(b.days).to.eql(2); pm.expect(b.endDate > b.startDate).to.be.true; pm.expect(b.estimatedCost).to.eql(Number(pm.collectionVariables.get("carFullCost"))); pm.expect(b.number).to.match(/^VS-\\d+$/); pm.expect(b.canCancel).to.be.true; pm.expect(b.canStart).to.be.false; }});
pm.collectionVariables.set("fullBookingId", b.id);
"""),
        req("The same vehicle cannot overlap itself", "POST", "/api/bookings", token="customerToken", body=booking(date="{{d2}}", service="{{basicId}}"), tests=f"""
{status(409)}
pm.test("Says so", () => pm.expect(pm.response.json().message).to.include("already has a service"));
"""),
        req("The bike may use the same day", "POST", "/api/bookings", token="customerToken", body=booking("{{bikeId}}", "{{d1}}", "{{basicId}}"), tests=f"""
{status(201)}
pm.test("Bike estimate is lower", () => pm.expect(pm.response.json().estimatedCost).to.be.below(2500));
pm.collectionVariables.set("bikeBookingId", pm.response.json().id);
"""),
        req("The day now shows the two vehicles", "GET", "/api/catalog/availability?from={{d1}}&days=2", token="customerToken", tests=f"""
{status(200)}
pm.test("First day has at least 2 booked", () => pm.expect(pm.response.json()[0].booked).to.be.at.least(2));
"""),
        req("Upcoming bookings", "GET", "/api/bookings/my?scope=upcoming", token="customerToken", tests=f"""
{status(200)}
pm.test("Both, soonest first", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].startDate <= l[1].startDate).to.be.true; }});
"""),
        req("Previous bookings are empty", "GET", "/api/bookings/my?scope=previous", token="customerToken", tests=f"""
{status(200)}
pm.test("None yet", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("A bad scope is rejected", "GET", "/api/bookings/my?scope=soon", token="customerToken", tests=status(400)),
        req("The garage finds the bookings by number", "GET", "/api/admin/bookings?search={{carNo}}", token="adminToken", tests=f"""
{status(200)}
pm.test("One booking for the car", () => {{ pm.expect(pm.response.json().length).to.eql(1); pm.expect(pm.response.json()[0].customerUsername).to.eql(pm.collectionVariables.get("newUser")); }});
"""),
    ]),
    folder("5. The garage's work", [
        req("Customers cannot start a service", "PUT", "/api/admin/bookings/{{bikeBookingId}}/start", token="customerToken", tests=status(403)),
        req("A service cannot start before its day", "PUT", "/api/admin/bookings/{{bikeBookingId}}/start", token="adminToken", tests=status(400)),
        req("Completing a booked vehicle is a conflict", "PUT", "/api/admin/bookings/{{bikeBookingId}}/complete", token="adminToken", body={}, tests=status(409)),
        req("Book the bike for today", "POST", "/api/bookings", token="customerToken", body=booking("{{bikeId}}", "{{today}}", "{{basicId}}"), tests=f"""
{status(201)}
pm.test("Can be started", () => pm.expect(pm.response.json().canStart).to.be.true);
pm.collectionVariables.set("todayId", pm.response.json().id);
"""),
        req("Start it", "PUT", "/api/admin/bookings/{{todayId}}/start", token="adminToken", tests=f"""
{status(200)}
pm.test("In service by the admin", () => {{ const b = pm.response.json(); pm.expect(b.status).to.eql("IN_SERVICE"); pm.expect(b.startedBy).to.eql("serviceadmin"); pm.expect(b.canCancel).to.be.false; }});
"""),
        req("Starting twice is a conflict", "PUT", "/api/admin/bookings/{{todayId}}/start", token="adminToken", tests=status(409)),
        req("The customer cannot cancel work in progress", "DELETE", "/api/bookings/{{todayId}}", token="customerToken", tests=status(409)),
        req("A negative final cost is rejected", "PUT", "/api/admin/bookings/{{todayId}}/complete", token="adminToken", body={"finalCost": -5}, tests=f"""
{status(400)}
pm.test("Cost error", () => pm.expect(pm.response.json().fieldErrors.finalCost).to.include("negative"));
"""),
        req("Complete it with the final bill", "PUT", "/api/admin/bookings/{{todayId}}/complete", token="adminToken",
            body={"finalCost": 1450.5, "workNotes": "Oil changed, chain lubricated"}, tests=f"""
{status(200)}
pm.test("Completed with the bill", () => {{ const b = pm.response.json(); pm.expect(b.status).to.eql("COMPLETED"); pm.expect(b.finalCost).to.eql(1450.5); pm.expect(b.completedBy).to.eql("serviceadmin"); }});
"""),
        req("Completing twice is a conflict", "PUT", "/api/admin/bookings/{{todayId}}/complete", token="adminToken", body={}, tests=status(409)),
        req("The bike's history shows the finished job", "GET", "/api/vehicles/{{bikeId}}/history", token="customerToken", tests=f"""
{status(200)}
pm.test("One finished service with notes", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].workNotes).to.include("chain"); }});
"""),
        req("Another customer cannot read it", "GET", "/api/vehicles/{{bikeId}}/history", token="otherToken", tests=status(403)),
        req("The garage finds the bike and its history count", "GET", "/api/admin/vehicles?search={{bikeNo}}", token="adminToken", tests=f"""
{status(200)}
pm.test("One vehicle, one service done", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].completedServices).to.eql(1); pm.expect(l[0].ownerUsername).to.eql(pm.collectionVariables.get("newUser")); }});
"""),
        req("The garage reads the same history", "GET", "/api/admin/vehicles/{{bikeId}}/history", token="adminToken", tests=f"""
{status(200)}
pm.test("One entry", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("A vehicle with bookings cannot be removed", "DELETE", "/api/vehicles/{{bikeId}}", token="customerToken", tests=status(400)),
    ]),
    folder("6. Cancelling", [
        req("Another customer cannot cancel it", "DELETE", "/api/bookings/{{fullBookingId}}", token="otherToken", tests=status(403)),
        req("The customer cancels with a reason", "DELETE", "/api/bookings/{{fullBookingId}}?reason=Out%20of%20town", token="customerToken", tests=f"""
{status(200)}
pm.test("Cancelled by the customer", () => {{ const b = pm.response.json(); pm.expect(b.status).to.eql("CANCELLED"); pm.expect(b.cancelReason).to.eql("Out of town"); pm.expect(b.cancelledBy).to.eql(pm.collectionVariables.get("newUser")); }});
"""),
        req("Cancelling twice is a conflict", "DELETE", "/api/bookings/{{fullBookingId}}", token="customerToken", tests=status(409)),
        req("The car can book those days again", "POST", "/api/bookings", token="customerToken", body=booking(date="{{d3}}", service="{{basicId}}"), tests=f"""
{status(201)}
pm.collectionVariables.set("secondId", pm.response.json().id);
"""),
        req("The garage must give a reason", "PUT", "/api/admin/bookings/{{secondId}}/cancel", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("Reason error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("reason"));
"""),
        req("The garage cancels with a reason", "PUT", "/api/admin/bookings/{{secondId}}/cancel", token="adminToken", body={"reason": "Mechanic unavailable"}, tests=f"""
{status(200)}
pm.test("Cancelled by the admin", () => {{ const b = pm.response.json(); pm.expect(b.status).to.eql("CANCELLED"); pm.expect(b.cancelledBy).to.eql("serviceadmin"); }});
"""),
        req("The customer sees the garage's reason", "GET", "/api/bookings/my?scope=previous", token="customerToken", tests=f"""
{status(200)}
pm.test("Previous holds the finished and cancelled ones", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(3); pm.expect(l.find(b => b.id == pm.collectionVariables.get("secondId")).cancelReason).to.eql("Mechanic unavailable"); }});
"""),
        req("Cancel the bike's future booking too", "DELETE", "/api/bookings/{{bikeBookingId}}", token="customerToken", tests=status(200)),
        req("Unknown booking is 404", "DELETE", "/api/bookings/999999", token="customerToken", tests=status(404)),
    ]),
    folder("7. Services, bays and the dashboard", [
        req("A bad service form lists the problems", "POST", "/api/admin/services", token="adminToken", body={"name": "", "durationDays": 9, "basePrice": 5}, tests=f"""
{status(400)}
pm.test("All three", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("name", "durationDays", "basePrice"));
"""),
        req("Create a service", "POST", "/api/admin/services", token="adminToken", body=service(), tests=f"""
{status(201)}
pm.test("Active", () => pm.expect(pm.response.json().active).to.be.true);
pm.collectionVariables.set("newServiceId", pm.response.json().id);
"""),
        req("A duplicate name is rejected", "POST", "/api/admin/services", token="adminToken", body=service("{{serviceName}}"), tests=status(400)),
        req("Customers cannot create services", "POST", "/api/admin/services", token="customerToken", body=service("Nope"), tests=status(403)),
        req("Customers see the new service", "GET", "/api/catalog/services", token="customerToken", tests=f"""
{status(200)}
pm.test("Listed", () => pm.expect(pm.response.json().some(s => s.id == pm.collectionVariables.get("newServiceId"))).to.be.true);
"""),
        req("Switch it off", "PUT", "/api/admin/services/{{newServiceId}}", token="adminToken", body=service(price=1100, active=False), tests=f"""
{status(200)}
pm.test("Inactive at the new price", () => {{ pm.expect(pm.response.json().active).to.be.false; pm.expect(pm.response.json().basePrice).to.eql(1100); }});
"""),
        req("A switched-off service cannot be booked", "POST", "/api/bookings", token="customerToken", body=booking(date="{{d4}}", service="{{newServiceId}}"), tests=status(400)),
        req("Customers no longer see it", "GET", "/api/catalog/services", token="customerToken", tests=f"""
{status(200)}
pm.test("Hidden", () => pm.expect(pm.response.json().some(s => s.id == pm.collectionVariables.get("newServiceId"))).to.be.false);
"""),
        req("Zero bays is rejected", "PUT", "/api/admin/bays", token="adminToken", body={"bays": 0}, tests=f"""
{status(400)}
pm.test("Bays error", () => pm.expect(pm.response.json().fieldErrors.bays).to.include("At least 1"));
"""),
        req("Dashboard", "GET", "/api/admin/dashboard", token="adminToken", tests=f"""
{status(200)}
pm.test("Bays, today, month and the next two weeks", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("bays", "baysUsedToday", "arrivalsToday", "inServiceNow", "upcomingBooked", "completedThisMonth", "revenueThisMonth", "topServices", "load");
  pm.expect(d.load.length).to.eql(14);
  pm.expect(d.completedThisMonth).to.be.at.least(1);
}});
"""),
    ]),
    folder("8. Security checks", [
        req("No token is 401", "GET", "/api/bookings/my", tests=status(401)),
        req("Customer cannot see the schedule", "GET", "/api/admin/bookings", token="customerToken", tests=status(403)),
        req("Customer cannot see the dashboard", "GET", "/api/admin/dashboard", token="customerToken", tests=status(403)),
        req("Customer cannot search all vehicles", "GET", "/api/admin/vehicles", token="customerToken", tests=status(403)),
        req("Customer cannot change the bays", "PUT", "/api/admin/bays", token="customerToken", body={"bays": 9}, tests=status(403)),
        req("Admin cannot book", "POST", "/api/bookings", token="adminToken", body=booking(), tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Vehicle Service Booking API",
    "Walks the workshop workflow: registering vehicles, services priced by vehicle type, free days, booking with overlap protection, "
    "BOOKED to IN_SERVICE to COMPLETED, history, cancellations with reasons, service management, bays, the dashboard and security.\n"
    "Start the backend first (http://localhost:8102). Repeatable: it registers a fresh customer, random vehicle numbers and books days about two months ahead.",
    items, {"baseUrl": "http://localhost:8102", **{k: "" for k in [
        "adminToken", "customerToken", "otherToken", "runId", "newUser", "carNo", "carNoSpaced", "bikeNo", "serviceName", "today", "yesterday", "far",
        "d1", "d2", "d3", "d4", "carId", "bikeId", "fullId", "basicId", "carFullCost", "fullBookingId", "bikeBookingId", "todayId", "secondId", "newServiceId"]}})
