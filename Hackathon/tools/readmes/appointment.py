"""python tools/readmes/appointment.py appointment-booking/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Appointment Booking System",
    "summary": "Providers (doctors, a dentist, a physiotherapist, a counsellor, a nutritionist) publish slots; customers browse providers, pick an open time on a calendar and book; "
               "the provider confirms or cancels with a reason. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8120, "frontend_port": 5120, "db": "appointment_booking", "package": "appointment",
    "logins": [("`dr.mehta` (General physician; also `dr.rao`, `kavita.joshi`, `sanjay.kulkarni`, `ritu.bansal`)", "`provider123`", "SERVICE_PROVIDER"),
               ("`aarav.sharma` (an upcoming confirmed appointment, a pending one, a past one and a cancelled one)", "`customer123`", "CUSTOMER"),
               ("`diya.patel`, `rohan.mehta`, … (30 customers)", "`customer123`", "CUSTOMER")],
    "features": [
        "**Provider:** creates slots one at a time or a whole stretch at once (for example 09:00 to 12:00 in 30-minute pieces), sees a week calendar coloured by open, pending and confirmed, "
        "confirms or cancels appointments (a reason is mandatory), withdraws open slots, edits the public profile and sees a dashboard (today, to confirm, open slots, how full the week is).",
        "**Customer:** searches providers by name, speciality or place, sees their open slots day by day, books with an optional note, follows upcoming and previous appointments and cancels up to two hours before.",
        "Calendar UI, appointment search and cancellation reason (the case's bonus features).",
    ],
    "rules": [
        ("A slot cannot be double-booked: one live (pending or confirmed) appointment per slot", "`409`"),
        ("Past slots cannot be booked; slots can only be created for the future, up to 60 days ahead, 15 to 180 minutes long and ending the same day", "`400` field errors"),
        ("A provider's slots cannot overlap (a slot may start exactly when another ends)", "`409`"),
        ("A customer cannot hold two live appointments at the same time, with any provider", "`409`"),
        ("Only the provider of a slot confirms or cancels its appointment, and must give a reason to cancel", "`403` / `400`"),
        ("Only a pending appointment can be confirmed, only a live one cancelled; a past appointment can be neither", "`409` / `400`"),
        ("A customer can cancel only their own appointment and only up to 2 hours before it starts", "`403` / `400`"),
        ("A slot holding a live appointment cannot be withdrawn", "`409`"),
        ("Self-registration creates customers only; providers are created by the practice", "role is ignored"),
    ],
    "concurrency": "booking, confirming and cancelling take a row lock on the slot (under `READ_COMMITTED`, and before the appointment is loaded), booking also locks the customer, "
                   "and slot creation locks the provider, so five customers racing for one slot make one appointment, one customer clicking two overlapping slots gets only one, "
                   "and two requests creating the same window make one set of slots. The tests check all of this with parallel threads.",
    "notes": [
        "Provider pictures are looked up by speciality (`/providers/<speciality>.jpg`); without one the provider's initials are shown on a coloured disc.",
        "Slots are shown in the server's local time; there is no time-zone handling.",
    ],
    "api": [
        ("GET", "/api/providers?search=", "any signed-in user", "Providers with free slots in the next 14 days and the first free time"),
        ("GET", "/api/providers/{id}/slots?from=&days=", "any signed-in user", "Upcoming slots, each AVAILABLE or taken (no customer details)"),
        ("POST", "/api/appointments", "CUSTOMER", "Book `{slotId, reason?}`"),
        ("GET", "/api/appointments/my?scope=upcoming|previous", "CUSTOMER", "Own appointments"),
        ("DELETE", "/api/appointments/{id}?reason=", "CUSTOMER", "Cancel up to 2 hours before"),
        ("GET", "/api/provider/slots?from=&days=", "SERVICE_PROVIDER", "Own calendar with customers"),
        ("POST", "/api/provider/slots", "SERVICE_PROVIDER", "One slot `{date, startTime, minutes}`"),
        ("POST", "/api/provider/slots/bulk", "SERVICE_PROVIDER", "Many slots `{date, fromTime, toTime, minutes}` (all or nothing)"),
        ("DELETE", "/api/provider/slots/{id}", "SERVICE_PROVIDER", "Withdraw an unbooked slot"),
        ("GET", "/api/provider/appointments?status=&date=&search=", "SERVICE_PROVIDER", "Own appointments, pending first"),
        ("PUT", "/api/provider/appointments/{id}/confirm", "SERVICE_PROVIDER", "Confirm"),
        ("PUT", "/api/provider/appointments/{id}/cancel", "SERVICE_PROVIDER", "Cancel with a reason `{reason}`"),
        ("GET/PUT", "/api/provider/profile", "SERVICE_PROVIDER", "Read or edit the public profile"),
        ("GET", "/api/provider/dashboard", "SERVICE_PROVIDER", "Today, to confirm, open slots, utilisation, 7 days, next appointments"),
    ],
    "controllers": "AuthController, CustomerController, ProviderController",
    "services": "AppointmentService (providers, slots, booking, decisions, profile, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository (row lock), ProviderRepository (row lock), SlotRepository (row lock), AppointmentRepository",
    "entities": "User, Provider, Slot, Appointment, Role, AppointmentStatus",
    "tables": "`users`, `providers` (profile of a provider account), `slots` (provider FK, start, end, closed) and `appointments` (slot and customer FKs, status, reason, who confirmed or cancelled and why).",
    "tests": "34 tests on in-memory H2 (no MySQL needed): slot rules, bulk creation, browsing, booking, overlaps, notice period, provider decisions, search, profile, dashboard, roles, parallel bookings and slot creation",
    "collection": "Appointment.postman_collection.json",
    "postman": "69 requests (107 assertions). It registers a fresh customer and creates slots three to eight weeks ahead in the early morning, then restores the profile it edits, so it can be run many times",
    "limits": [
        "No reminders by email or SMS, no recurring slots and no waiting list.",
        "A slot is open to everyone; a provider cannot restrict it to certain customers.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
