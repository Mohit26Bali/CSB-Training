"""python tools/collections/hotel.py hotel-booking/docs/postman/Hotel.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
const day = (offset) => { const d = new Date(Date.now() + offset * 864e5); return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0"); };
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("roomNo", "P" + id.slice(0, 5));
pm.collectionVariables.set("in1", day(150));
pm.collectionVariables.set("out1", day(153));
pm.collectionVariables.set("in2", day(152));
pm.collectionVariables.set("out2", day(155));
pm.collectionVariables.set("in3", day(153));
pm.collectionVariables.set("out3", day(155));
pm.collectionVariables.set("yesterday", day(-1));
"""


def room(number="{{roomNo}}", rtype="suite", price=12000, capacity=3, active=True):
    return {"number": number, "type": rtype, "floor": 9, "pricePerNight": price, "capacity": capacity,
            "description": "Created by the Postman run", "amenities": "Wi-Fi, Minibar", "active": active}


def stay(check_in="{{in1}}", check_out="{{out1}}", guests=2):
    return {"roomId": "{{roomId}}", "checkIn": check_in, "checkOut": check_out, "guests": guests, "specialRequests": "High floor please"}


items = [
    folder("1. Auth", [
        req("Login as hotel admin", "POST", "/api/auth/login", body={"username": "hoteladmin", "password": "admin123"},
            tests=login_tests("adminToken"), pre=PRE),
        req("Register a fresh guest for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Guest", "role": "HOTEL_ADMIN"}, tests=f"""
{status(201)}
pm.test("Always a guest", () => pm.expect(pm.response.json().role).to.eql("CUSTOMER"));
pm.collectionVariables.set("guestToken", pm.response.json().token);
"""),
        req("Login as another guest", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "customer123"},
            tests=login_tests("guest2Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "hoteladmin", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Admin creates a room", [
        req("Empty form lists every problem", "POST", "/api/admin/rooms", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("number", "type", "floor", "pricePerNight", "capacity"));
"""),
        req("A tiny price is rejected", "POST", "/api/admin/rooms", token="adminToken", body=room(price=50), tests=f"""
{status(400)}
pm.test("Price error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("pricePerNight"));
"""),
        req("Unknown room type is rejected", "POST", "/api/admin/rooms", token="adminToken", body=room(rtype="palace"), tests=f"""
{status(400)}
pm.test("Type error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("type"));
"""),
        req("Create a suite", "POST", "/api/admin/rooms", token="adminToken", body=room(), tests=f"""
{status(201)}
const r = pm.response.json();
pm.test("Active suite with amenities", () => {{ pm.expect(r.type).to.eql("SUITE"); pm.expect(r.active).to.be.true; pm.expect(r.amenities).to.eql(["Wi-Fi", "Minibar"]); }});
pm.collectionVariables.set("roomId", r.id);
"""),
        req("A duplicate number is rejected", "POST", "/api/admin/rooms", token="adminToken", body=room(), tests=f"""
{status(400)}
pm.test("Number error", () => pm.expect(pm.response.json().fieldErrors.number).to.include("already exists"));
"""),
        req("Guests cannot create rooms", "POST", "/api/admin/rooms", token="guestToken", body=room("X1"), tests=status(403)),
    ]),
    folder("3. Guest searches", [
        req("Rooms free for the stay, with the price", "GET", "/api/rooms/available?checkIn={{in1}}&checkOut={{out1}}&type=suite", token="guestToken", tests=f"""
{status(200)}
pm.test("The new suite is listed with three nights costed", () => {{ const r = pm.response.json().find(x => x.id == pm.collectionVariables.get("roomId")); pm.expect(r.nights).to.eql(3); pm.expect(r.totalPrice).to.eql(36000); }});
"""),
        req("Fewer seats than guests hides the room", "GET", "/api/rooms/available?checkIn={{in1}}&checkOut={{out1}}&guests=9", token="guestToken", tests=f"""
{status(200)}
pm.test("Not listed", () => pm.expect(pm.response.json().some(r => r.id == pm.collectionVariables.get("roomId"))).to.be.false);
"""),
        req("Check-in in the past is rejected", "GET", "/api/rooms/available?checkIn={{yesterday}}&checkOut={{out1}}", token="guestToken", tests=f"""
{status(400)}
pm.test("Check-in error", () => pm.expect(pm.response.json().fieldErrors.checkIn).to.include("past"));
"""),
        req("Check-out before check-in is rejected", "GET", "/api/rooms/available?checkIn={{out1}}&checkOut={{in1}}", token="guestToken", tests=f"""
{status(400)}
pm.test("Check-out error", () => pm.expect(pm.response.json().fieldErrors.checkOut).to.include("after"));
"""),
        req("Room types", "GET", "/api/rooms/types", token="guestToken", tests=f"""
{status(200)}
pm.test("Four types", () => pm.expect(pm.response.json()).to.eql(["STANDARD", "DELUXE", "SUITE", "FAMILY"]));
"""),
    ]),
    folder("4. Guests book", [
        req("Empty booking lists the problems", "POST", "/api/bookings", token="guestToken", body={}, tests=f"""
{status(400)}
pm.test("Fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("roomId", "checkIn", "checkOut", "guests"));
"""),
        req("Too many guests for the room", "POST", "/api/bookings", token="guestToken", body=stay(guests=6), tests=f"""
{status(400)}
pm.test("Guests error", () => pm.expect(pm.response.json().fieldErrors.guests).to.include("sleeps at most"));
"""),
        req("Book the suite (pending)", "POST", "/api/bookings", token="guestToken", body=stay(), tests=f"""
{status(201)}
const b = pm.response.json();
pm.test("Pending, three nights, 36,000 total", () => {{ pm.expect(b.status).to.eql("PENDING"); pm.expect(b.nights).to.eql(3); pm.expect(b.totalPrice).to.eql(36000); pm.expect(b.number).to.match(/^HB-\\d+$/); pm.expect(b.canCancel).to.be.true; }});
pm.collectionVariables.set("bookingId", b.id);
"""),
        req("The same guest cannot overlap their own booking", "POST", "/api/bookings", token="guestToken", body=stay("{{in2}}", "{{out2}}"), tests=status(409)),
        req("Another guest may ask for overlapping dates", "POST", "/api/bookings", token="guest2Token", body=stay("{{in2}}", "{{out2}}"), tests=f"""
{status(201)}
pm.test("Also pending", () => pm.expect(pm.response.json().status).to.eql("PENDING"));
pm.collectionVariables.set("rivalId", pm.response.json().id);
"""),
        req("My bookings", "GET", "/api/bookings/my", token="guestToken", tests=f"""
{status(200)}
pm.test("Just mine", () => {{ pm.expect(pm.response.json().length).to.eql(1); pm.expect(pm.response.json()[0].phase).to.eql("UPCOMING"); }});
"""),
        req("Admin sees both pending requests", "GET", "/api/admin/bookings?status=PENDING&roomId={{roomId}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Two, soonest check-in first", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].checkIn <= l[1].checkIn).to.be.true; }});
"""),
    ]),
    folder("5. Admin confirms", [
        req("Guests cannot confirm", "PUT", "/api/admin/bookings/{{bookingId}}/confirm", token="guestToken", tests=status(403)),
        req("Confirm the first booking", "PUT", "/api/admin/bookings/{{bookingId}}/confirm", token="adminToken", tests=f"""
{status(200)}
pm.test("Confirmed and the rival released", () => {{ const b = pm.response.json(); pm.expect(b.status).to.eql("CONFIRMED"); pm.expect(b.confirmedBy).to.eql("hoteladmin"); pm.expect(b.autoCancelled).to.eql(1); }});
"""),
        req("The rival was released with a reason", "GET", "/api/bookings/my", token="guest2Token", tests=f"""
{status(200)}
pm.test("Cancelled by the system", () => {{ const b = pm.response.json().find(x => x.id == pm.collectionVariables.get("rivalId")); pm.expect(b.status).to.eql("CANCELLED"); pm.expect(b.cancelledBy).to.eql("system"); pm.expect(b.cancelReason).to.include("another guest"); }});
"""),
        req("Confirming again is a conflict", "PUT", "/api/admin/bookings/{{bookingId}}/confirm", token="adminToken", tests=status(409)),
        req("A released booking cannot be confirmed", "PUT", "/api/admin/bookings/{{rivalId}}/confirm", token="adminToken", tests=status(409)),
        req("The confirmed stay hides the room", "GET", "/api/rooms/available?checkIn={{in2}}&checkOut={{out2}}&type=suite", token="guestToken", tests=f"""
{status(200)}
pm.test("Not listed", () => pm.expect(pm.response.json().some(r => r.id == pm.collectionVariables.get("roomId"))).to.be.false);
"""),
        req("Overlapping bookings are now refused", "POST", "/api/bookings", token="guest2Token", body=stay("{{in2}}", "{{out2}}"), tests=f"""
{status(409)}
pm.test("Says not available", () => pm.expect(pm.response.json().message).to.include("not available"));
"""),
        req("A booking starting on the check-out day is allowed", "POST", "/api/bookings", token="guest2Token", body=stay("{{in3}}", "{{out3}}", 1), tests=f"""
{status(201)}
pm.collectionVariables.set("backToBackId", pm.response.json().id);
"""),
        req("...and can be confirmed too", "PUT", "/api/admin/bookings/{{backToBackId}}/confirm", token="adminToken", tests=f"""
{status(200)}
pm.test("No release needed", () => pm.expect(pm.response.json().autoCancelled).to.eql(0));
"""),
    ]),
    folder("6. Cancelling", [
        req("A room with live bookings cannot be closed", "PUT", "/api/admin/rooms/{{roomId}}", token="adminToken", body=room(active=False), tests=f"""
{status(400)}
pm.test("Says so", () => pm.expect(pm.response.json().message).to.include("upcoming or current bookings"));
"""),
        req("Another guest cannot cancel it", "DELETE", "/api/bookings/{{bookingId}}", token="guest2Token", tests=status(403)),
        req("The guest cancels the first booking", "DELETE", "/api/bookings/{{bookingId}}?reason=Plans%20changed", token="guestToken", tests=f"""
{status(200)}
pm.test("Cancelled with the reason", () => {{ const b = pm.response.json(); pm.expect(b.status).to.eql("CANCELLED"); pm.expect(b.cancelReason).to.eql("Plans changed"); pm.expect(b.cancelledBy).to.eql(pm.collectionVariables.get("newUser")); }});
"""),
        req("Cancelling twice is a conflict", "DELETE", "/api/bookings/{{bookingId}}", token="guestToken", tests=status(409)),
        req("The dates are free again", "GET", "/api/rooms/available?checkIn={{in1}}&checkOut={{in2}}&type=suite", token="guestToken", tests=f"""
{status(200)}
pm.test("Listed", () => pm.expect(pm.response.json().some(r => r.id == pm.collectionVariables.get("roomId"))).to.be.true);
"""),
        req("The hotel needs a reason to cancel", "PUT", "/api/admin/bookings/{{backToBackId}}/cancel", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("Reason error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("reason"));
"""),
        req("The hotel cancels the other booking", "PUT", "/api/admin/bookings/{{backToBackId}}/cancel", token="adminToken", body={"reason": "Pipe burst in the suite"}, tests=f"""
{status(200)}
pm.test("Cancelled by the admin", () => {{ const b = pm.response.json(); pm.expect(b.status).to.eql("CANCELLED"); pm.expect(b.cancelledBy).to.eql("hoteladmin"); }});
"""),
        req("Guest 2 sees the hotel's reason", "GET", "/api/bookings/my", token="guest2Token", tests=f"""
{status(200)}
pm.test("Visible", () => pm.expect(pm.response.json().find(x => x.id == pm.collectionVariables.get("backToBackId")).cancelReason).to.eql("Pipe burst in the suite"));
"""),
        req("Unknown booking is 404", "DELETE", "/api/bookings/999999", token="guestToken", tests=status(404)),
    ]),
    folder("7. Closing a room, the calendar and the dashboard", [
        req("Close the room (no live bookings left)", "PUT", "/api/admin/rooms/{{roomId}}", token="adminToken", body=room(price=13500, active=False), tests=f"""
{status(200)}
pm.test("Closed with the new price", () => {{ const r = pm.response.json(); pm.expect(r.active).to.be.false; pm.expect(r.pricePerNight).to.eql(13500); }});
"""),
        req("A closed room cannot be booked", "POST", "/api/bookings", token="guestToken", body=stay(), tests=status(400)),
        req("A closed room is hidden from searches", "GET", "/api/rooms/available?checkIn={{in1}}&checkOut={{out1}}&type=suite", token="guestToken", tests=f"""
{status(200)}
pm.test("Not listed", () => pm.expect(pm.response.json().some(r => r.id == pm.collectionVariables.get("roomId"))).to.be.false);
"""),
        req("Calendar for the month", "GET", "/api/admin/calendar", token="adminToken", tests=f"""
{status(200)}
pm.test("Rooms with days", () => {{ const c = pm.response.json(); pm.expect(c.days).to.be.at.least(28); pm.expect(c.rooms.length).to.be.at.least(1); }});
"""),
        req("Dashboard", "GET", "/api/admin/dashboard", token="adminToken", tests=f"""
{status(200)}
pm.test("Occupancy, arrivals, revenue and types", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("rooms", "activeRooms", "occupiedTonight", "occupancyPercent", "arrivalsToday", "departuresToday", "pendingBookings", "revenueThisMonth", "byType", "nextArrivals");
}});
"""),
    ]),
    folder("8. Security checks", [
        req("No token is 401", "GET", "/api/bookings/my", tests=status(401)),
        req("Guest cannot see admin bookings", "GET", "/api/admin/bookings", token="guestToken", tests=status(403)),
        req("Guest cannot see the dashboard", "GET", "/api/admin/dashboard", token="guestToken", tests=status(403)),
        req("Guest cannot cancel as the hotel", "PUT", "/api/admin/bookings/{{bookingId}}/cancel", token="guestToken", body={"reason": "x"}, tests=status(403)),
        req("Admin cannot book", "POST", "/api/bookings", token="adminToken", body=stay(), tests=status(403)),
        req("Admin has no personal bookings", "GET", "/api/bookings/my", token="adminToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Hotel Room Booking API",
    "Walks the hotel workflow: rooms, availability search with the stay price, booking, confirmation that releases rival requests, overlap protection, back-to-back stays, cancellation rules, closing a room, the calendar, the dashboard and security.\n"
    "Start the backend first (http://localhost:8108). Repeatable: it registers a fresh guest, creates a uniquely numbered suite and books dates about five months ahead.",
    items, {"baseUrl": "http://localhost:8108", **{k: "" for k in [
        "adminToken", "guestToken", "guest2Token", "roomId", "bookingId", "rivalId", "backToBackId", "runId", "newUser", "roomNo",
        "in1", "out1", "in2", "out2", "in3", "out3", "yesterday"]}})
