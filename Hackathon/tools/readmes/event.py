"""python tools/readmes/event.py event-registration/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Event Registration and Approval System",
    "summary": "Admins publish internal events; employees browse upcoming ones, register, and join a waitlist when an event is full. "
               "Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8118, "frontend_port": 5118, "db": "event_registration", "package": "event",
    "logins": [("`eventadmin`", "`admin123`", "EVENT_ADMIN"), ("`rahul.deshpande`", "`admin123`", "EVENT_ADMIN"),
               ("`aarav.sharma`, `diya.patel`, `neha.kapoor`, … (26 employees)", "`employee123`", "EMPLOYEE")],
    "features": [
        "**Employee:** browse upcoming published events (search by title, venue or description, filter by category) with live seat counts; "
        "register in one click; join the waitlist of a full event and see the position in the queue; cancel until the event starts; "
        "a list of all own registrations.",
        "**Event admin:** create events as drafts, edit, publish, cancel (which releases every registration); see the participants of "
        "each event (confirmed first, then the waitlist in order); a dashboard of seats filled and waitlist sizes per event.",
        "Search and filter, participant-count dashboard and waitlist (the case's bonus features).",
    ],
    "rules": [
        ("Past (already started) events and drafts or cancelled events do not accept registration", "`400`"),
        ("An employee cannot register twice for the same event (confirmed or on the waitlist)", "`409`"),
        ("Capacity is never exceeded; a full event is refused unless the employee explicitly joins the waitlist", "`409`, the message offers the waitlist"),
        ("When a confirmed registration is cancelled, or the capacity is raised, waitlisted people are confirmed automatically, oldest first", "automatic"),
        ("Capacity cannot be lowered below the number already confirmed", "`400` field error"),
        ("A new event must start in the future, end after it starts, use a known category and have a capacity between 1 and 5000", "`400` with a field error each"),
        ("Started or cancelled events cannot be edited; started events cannot be cancelled; only drafts can be published", "`400` / `409`"),
        ("Only the owner can cancel a registration, and only once", "`403` / `409`"),
        ("Only admins manage events; only employees register", "`403`"),
    ],
    "concurrency": "registering takes a row lock on the event (under `READ_COMMITTED`), so when many employees go for the last seat exactly one "
                   "wins and the rest get a clear `409`; a unique (event, employee) constraint settles double clicks; cancelling and capacity "
                   "changes use the same lock, so the waitlist never over-fills. The tests check all three with parallel threads.",
    "notes": [
        "Cancelling keeps the registration row (status CANCELLED) and registering again re-activates it at the back of the queue, which keeps the unique constraint simple.",
        "Each event response carries the caller's own status (`myStatus`, `myRegistrationId`) so the UI shows Register, Join waitlist or Cancel.",
    ],
    "api": [
        ("GET", "/api/events?search=&category=", "any", "Upcoming published events with seat counts and own status"),
        ("GET", "/api/events/categories", "any", "Event categories"),
        ("POST", "/api/registrations", "EMPLOYEE", "Register `{eventId, joinWaitlist?}`"),
        ("GET", "/api/registrations/my", "EMPLOYEE", "Own registrations with waitlist position"),
        ("DELETE", "/api/registrations/{id}", "EMPLOYEE", "Cancel own registration (promotes the waitlist)"),
        ("GET", "/api/admin/events?status=", "EVENT_ADMIN", "All events"),
        ("POST", "/api/admin/events", "EVENT_ADMIN", "Create a draft `{title, category, venue, description, startsAt, endsAt, capacity}`"),
        ("PUT", "/api/admin/events/{id}", "EVENT_ADMIN", "Edit an event"),
        ("PUT", "/api/admin/events/{id}/publish", "EVENT_ADMIN", "Publish a draft"),
        ("PUT", "/api/admin/events/{id}/cancel", "EVENT_ADMIN", "Cancel an event and its registrations"),
        ("GET", "/api/admin/events/{id}/participants", "EVENT_ADMIN", "Confirmed participants, then the waitlist"),
        ("GET", "/api/admin/dashboard", "EVENT_ADMIN", "Seats filled and waitlists per event"),
    ],
    "controllers": "AuthController, EventController (employee), AdminController",
    "services": "EventService (events, dashboard), RegistrationService (capacity, waitlist, locking), AuthService, LoginAttemptService",
    "repos": "UserRepository, EventRepository (row lock), RegistrationRepository",
    "entities": "User, Event, Registration, Role, EventStatus, RegistrationStatus",
    "tables": "`users`, `events` and `registrations` (event and user FKs, **UNIQUE (event, user)**, status, registration time).",
    "tests": "27 tests on in-memory H2 (no MySQL needed): registration rules, capacity, waitlist promotion, admin rules, roles, parallel registrations",
    "collection": "Event.postman_collection.json",
    "postman": "55 requests (92 assertions). It registers a fresh employee and creates a uniquely named event each run, so it can be run many times",
    "limits": [
        "Registrations are not approved individually: they are confirmed automatically while seats last (the waitlist covers the rest).",
        "There are no email notifications when a waitlisted person is promoted.",
        "Searching and filtering is done in memory (Java Streams), which is fine for one company's events but would need paging at scale.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
