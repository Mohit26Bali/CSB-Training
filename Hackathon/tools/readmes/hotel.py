"""python tools/readmes/hotel.py hotel-booking/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Hotel Room Booking System",
    "summary": "Guests search rooms by dates, type and party size, see the price for the stay and request a booking; the hotel confirms or cancels, "
               "sees a month calendar of every room and a dashboard of occupancy and revenue. Spring Boot REST API with JWT security, plus a React front end with room photos.",
    "backend_port": 8108, "frontend_port": 5108, "db": "hotel_booking", "package": "hotel",
    "logins": [("`hoteladmin`", "`admin123`", "HOTEL_ADMIN"), ("`reception`", "`admin123`", "HOTEL_ADMIN"),
               ("`aarav.sharma` (an upcoming confirmed stay in room 302, a past stay, a pending request and a cancelled one)", "`customer123`", "CUSTOMER"),
               ("`diya.patel`, `rohan.mehta`, … (30 guests)", "`customer123`", "CUSTOMER")],
    "features": [
        "**Guest:** search available rooms for check-in/check-out dates with type, guests and price filters, see nights and total price, request a booking, "
        "see own bookings (upcoming, in stay, past) and cancel one before the check-in day.",
        "**Hotel:** a booking queue (pending first) with confirm and cancel-with-reason, a month calendar with a bar for every stay in every room, "
        "room management (add, edit, change price, close a room) and a dashboard with tonight's occupancy, arrivals, departures, revenue and a split by room type.",
        "Room photos by type, price calculation and occupancy statistics (the case's bonus features).",
    ],
    "rules": [
        ("Check-in cannot be in the past and check-out must be after check-in; a stay is at most 30 nights", "`400` field errors on `checkIn` / `checkOut`"),
        ("The number of guests must fit the room", "`400` field error on `guests`"),
        ("A room cannot be booked for dates that overlap a confirmed stay (check-out day is free for the next guest)", "`409`"),
        ("A guest cannot hold two overlapping bookings of their own", "`409`"),
        ("Confirming a booking automatically releases other pending requests for the same room and overlapping dates, with a reason", "`autoCancelled` count in the response"),
        ("A guest can cancel only their own booking, and only before the check-in day; the hotel must give a reason", "`403` / `409` / `400`"),
        ("A booking can be confirmed or cancelled only while in the right status", "`409`"),
        ("Room numbers are unique; a room with upcoming or current bookings cannot be closed", "`400`"),
        ("Closed rooms are hidden from searches and cannot be booked", "`400`"),
    ],
    "concurrency": "booking, confirming and cancelling take a row lock on the room (under `READ_COMMITTED`, and before loading the booking so no stale copy is used), "
                   "so two guests cannot both get the same room for the same night, and the hotel confirming while a guest cancels ends in one consistent state. "
                   "The tests check this with parallel threads.",
    "notes": [
        "Room photos are looked up by room type (`/rooms/<type>-1.jpg`); a room without one falls back to a coloured panel.",
        "No payment is taken: a confirmed booking is the end of the flow.",
    ],
    "api": [
        ("GET", "/api/rooms/available?checkIn=&checkOut=&type=&guests=&maxPrice=", "any signed-in user", "Rooms free for the stay with nights and total price"),
        ("GET", "/api/rooms/types", "any signed-in user", "Room types"),
        ("POST", "/api/bookings", "CUSTOMER", "Request a booking `{roomId, checkIn, checkOut, guests, specialRequests?}`"),
        ("GET", "/api/bookings/my", "CUSTOMER", "Own bookings"),
        ("DELETE", "/api/bookings/{id}?reason=", "CUSTOMER", "Cancel own booking before check-in day"),
        ("GET", "/api/admin/rooms", "HOTEL_ADMIN", "All rooms"),
        ("POST", "/api/admin/rooms", "HOTEL_ADMIN", "Add a room"),
        ("PUT", "/api/admin/rooms/{id}", "HOTEL_ADMIN", "Edit a room, change its price or close it"),
        ("GET", "/api/admin/bookings?status=&roomId=", "HOTEL_ADMIN", "Booking queue, pending first"),
        ("PUT", "/api/admin/bookings/{id}/confirm", "HOTEL_ADMIN", "Confirm and release rival requests"),
        ("PUT", "/api/admin/bookings/{id}/cancel", "HOTEL_ADMIN", "Cancel with a reason `{reason}`"),
        ("GET", "/api/admin/calendar?month=YYYY-MM", "HOTEL_ADMIN", "Stays per room for a month"),
        ("GET", "/api/admin/dashboard", "HOTEL_ADMIN", "Occupancy, arrivals, departures, revenue, by type"),
    ],
    "controllers": "AuthController, GuestController, AdminController",
    "services": "HotelService (search, booking, confirmation, cancellation, rooms, calendar, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository, RoomRepository (row lock), BookingRepository (overlap queries)",
    "entities": "User, Room, Booking, Role, RoomType, BookingStatus",
    "tables": "`users`, `rooms` (unique number, type, price, capacity, amenities, active) and `bookings` (room and guest FKs, stay dates, nights, total, status, who confirmed or cancelled and why).",
    "tests": "30 tests on in-memory H2 (no MySQL needed): search and date rules, booking, overlaps, back-to-back stays, confirmation releasing rivals, cancellation rules, room management, calendar, dashboard, roles, parallel bookings",
    "collection": "Hotel.postman_collection.json",
    "postman": "51 requests (85 assertions). It registers a fresh guest, creates a uniquely numbered suite and books dates about five months ahead, so it can be run many times",
    "limits": [
        "No payments, taxes or rate plans; the price is nights times the room's nightly price.",
        "Search and calendar are built in memory (Java Streams), which suits one hotel but would need paging for a chain.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
