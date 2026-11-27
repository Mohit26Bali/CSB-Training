"""Gives every project its own look. Edit LOOKS below, then:   python tools/apply_look.py [project ...]   (default: all)

For each project it applies the palette, fonts and radii (apply_theme), copies the shared AuthShell and Layout from tools/kit,
writes src/look.ts (login layout, navigation style, page banners) and downloads the banner photos from Pexels
(needs PEXELS_API_KEY; photos already on disk are kept).
"""
import json
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import apply_theme  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.dirname(os.path.abspath(__file__))

# palette, body font, heading font, card radius, control radius, login look as "photo style:headline animation:edge shape" (photo: slideshow kenburns duotone mosaic; headline: rotate typewriter slide fade; edge: wave diagonal stripes curve; the photo panel is always on the left), nav style, banners {route: (file, photo query, title, text)}
LOOKS = {
    "enrollment": ("teal", "DM Sans", "Fraunces", "0.75rem", "0.5rem", "slideshow:rotate:wave", "top", {
        "/courses": ("courses", "classroom training workshop employees", "Learn something new", "Every course in one place, with the seats left."),
        "/my-enrollments": ("my-enrollments", "online learning laptop notes", "Your learning", "Requests, approvals and what you are taking."),
        "/admin": ("admin", "training manager presentation team", "Training overview", "Demand, capacity and requests at a glance."),
        "/admin/courses": ("admin-courses", "course books desk stationery", "Courses", "Create, edit and schedule training."),
        "/admin/requests": ("admin-requests", "approval checklist office desk", "Requests", "Approve or decline, one seat at a time."),
    }),
    "expense-management": ("blue", "Plus Jakarta Sans", "Space Grotesk", "0.5rem", "0.375rem", "mosaic:typewriter:diagonal", "side", {
        "/expenses": ("expenses", "receipts expense reports desk", "Your expenses", "Claim it, track it, get paid back."),
        "/manager": ("manager", "finance dashboard laptop charts", "Spend overview", "Where the team's money goes."),
        "/manager/review": ("review", "accountant reviewing invoices", "Review queue", "Approve fairly and keep the budget honest."),
    }),
    "leave-portal": ("emerald", "Nunito", "Quicksand", "1rem", "0.75rem", "kenburns:slide:curve", "top", {
        "/leave": ("leave", "relaxing beach vacation", "Time away", "Balances, requests and what is coming up."),
        "/manager/review": ("review", "team meeting manager office", "Leave requests", "Decide with the team calendar in view."),
        "/manager/calendar": ("calendar", "calendar planning desk", "Team calendar", "Who is out, and when."),
    }),
    "procurement": ("orange", "Outfit", "Oswald", "0.25rem", "0.25rem", "duotone:fade:stripes", "brand", {
        "/requests": ("requests", "warehouse boxes inventory shelves", "Your requests", "What you asked for and where it stands."),
        "/manager": ("manager", "logistics supply chain trucks", "Procurement overview", "Budget, orders and deliveries."),
        "/manager/review": ("review", "purchase order paperwork desk", "Review queue", "Approve, order and track delivery."),
    }),
    "scholarship": ("violet", "Lexend", "Playfair Display", "0.75rem", "0.5rem", "slideshow:typewriter:curve", "top", {
        "/programs": ("programs", "graduation students campus", "Scholarships", "Find the ones you qualify for."),
        "/applications": ("applications", "student studying library notes", "Your applications", "Scores, status and decisions."),
        "/admin": ("admin", "university administration building", "Statistics", "Applications, awards and budget."),
        "/admin/applications": ("admin-applications", "reviewing application documents desk", "Applications", "Review, score and decide."),
        "/admin/programs": ("admin-programs", "college campus students walking", "Scholarships", "Create and manage programmes."),
    }),
    "warranty": ("rose", "Figtree", "Bricolage Grotesque", "1.25rem", "0.75rem", "mosaic:slide:wave", "side", {
        "/products": ("products", "electronics gadgets laptop phone desk", "Your products", "Warranty dates at a glance."),
        "/claims": ("claims", "repair technician fixing phone", "Your claims", "From filed to fixed."),
        "/agent": ("agent", "customer support service desk headset", "Claims queue", "Oldest and most urgent first."),
    }),
    "referral": ("lime", "Rubik", "Rubik", "0.75rem", "0.5rem", "duotone:rotate:stripes", "brand", {
        "/jobs": ("jobs", "team meeting office hiring", "Open positions", "Know someone great? Refer them."),
        "/referrals": ("referrals", "handshake interview office", "Your referrals", "Follow each one through the pipeline."),
        "/recruiter": ("recruiter", "recruiter interviewing candidate", "Hiring overview", "Where every referral stands."),
        "/recruiter/referrals": ("recruiter-referrals", "resume review hiring desk", "Referrals", "One stage at a time."),
        "/recruiter/jobs": ("recruiter-jobs", "job posting laptop office", "Jobs", "Draft, publish and close roles."),
    }),
    "event-registration": ("fuchsia", "Poppins", "Fredoka", "1.5rem", "1rem", "kenburns:fade:wave", "top", {
        "/events": ("events", "conference audience stage lights", "What is on", "Grab a seat before it is gone."),
        "/registrations": ("registrations", "event tickets badge lanyard", "Your seats", "Registrations and waitlist places."),
        "/admin": ("admin", "event planning team whiteboard", "Event overview", "How full is everything?"),
        "/admin/events": ("admin-events", "event venue setup chairs", "Events", "Draft, publish and manage."),
    }),
    "hostel": ("indigo", "Manrope", "Raleway", "0.75rem", "0.5rem", "kenburns:slide:diagonal", "side", {
        "/my-room": ("my-room", "dorm room bed desk student", "Your room", "Your bed and your requests."),
        "/rooms": ("rooms", "hostel corridor rooms doors", "Free rooms", "Beds that are ready to move into."),
        "/admin": ("admin", "hostel building exterior", "Occupancy", "Beds taken and beds free."),
        "/admin/requests": ("admin-requests", "reception desk keys hotel", "Requests", "The waiting list, oldest first."),
        "/admin/rooms": ("admin-rooms", "hostel bunk beds room", "Rooms", "Every room and who lives in it."),
        "/admin/residents": ("admin-residents", "students common room hostel", "Residents", "Who lives where."),
    }),
    "parking": ("cyan", "Sora", "Chakra Petch", "0.5rem", "0.25rem", "duotone:typewriter:stripes", "brand", {
        "/my-parking": ("my-parking", "parking garage cars", "Your parking", "Your slot and your requests."),
        "/slots": ("slots", "parking lot aerial view", "Free slots", "Green means free."),
        "/admin": ("admin", "parking structure ramp", "Lot overview", "Floor by floor."),
        "/admin/requests": ("admin-requests", "car dashboard parking ticket", "Requests", "The queue, oldest first."),
        "/admin/slots": ("admin-slots", "parking spaces painted lines", "Slot map", "The whole lot on one page."),
        "/admin/allocations": ("admin-allocations", "parked cars row", "Allocations", "Who holds which slot."),
    }),
    "helpdesk": ("sky", "IBM Plex Sans", "Barlow Semi Condensed", "0.5rem", "0.375rem", "duotone:typewriter:wave", "side", {
        "/tickets": ("tickets", "it support helping employee laptop", "Your tickets", "Raise it once, follow it to the fix."),
        "/agent": ("agent", "it support team control room monitors", "Support overview", "Deadlines, load and what is waiting."),
        "/agent/queue": ("queue", "help desk headset agent computer", "The queue", "Most urgent first."),
    }),
    "complaints": ("red", "Source Sans 3", "Libre Baskerville", "0.5rem", "0.375rem", "mosaic:slide:stripes", "top", {
        "/complaints": ("complaints", "customer service desk handshake", "Your complaints", "Tell us once, follow it to the answer."),
        "/agent": ("agent", "customer support analytics monitors", "Service overview", "Volume, speed and satisfaction."),
        "/agent/queue": ("queue", "customer support agent working headset", "The queue", "Most urgent, longest waiting first."),
    }),
    "maintenance": ("slate", "Work Sans", "Archivo", "0.5rem", "0.375rem", "mosaic:rotate:diagonal", "side", {
        "/requests": ("requests", "apartment building community residents", "Your requests", "Reported once, tracked to done."),
        "/manager": ("manager", "facility management team building", "Community overview", "Everything that needs fixing, at a glance."),
        "/manager/requests": ("requests-queue", "maintenance technician working apartment", "Requests", "Emergencies first, then the longest waiting."),
        "/manager/technicians": ("technicians", "plumber electrician tools team", "Technicians", "Who can do what, and how busy they are."),
    }),
    "visitors": ("pink", "Nunito Sans", "DM Serif Display", "1rem", "0.625rem", "kenburns:fade:diagonal", "top", {
        "/invitations": ("invitations", "business visitor welcome reception office", "Your visitors", "Invite once, they walk straight in."),
        "/gate": ("gate", "security officer reception lobby", "The gate", "Who is expected, who is inside."),
        "/gate/reports": ("report", "office lobby visitors sign in", "Daily report", "Numbers for today and the week."),
    }),
    "hotel-booking": ("navy", "Lato", "Cormorant Garamond", "0.75rem", "0.5rem", "kenburns:fade:curve", "top", {
        "/rooms": ("rooms", "luxury hotel room interior elegant", "Find your room", "Real availability, real prices, no surprises."),
        "/bookings": ("bookings", "hotel reception bell luggage", "Your stays", "Requests, confirmations and memories."),
        "/admin": ("admin", "hotel manager lobby team", "The hotel tonight", "Occupancy, arrivals and revenue."),
        "/admin/bookings": ("admin-bookings", "hotel front desk check in", "Bookings", "Confirm, release or cancel."),
        "/admin/calendar": ("admin-calendar", "hotel corridor doors rooms", "Availability calendar", "Every room, every night."),
        "/admin/rooms": ("admin-rooms", "hotel suite bedroom luxury", "Rooms", "Prices, photos and whether they are open."),
    }),
    "vehicle-service": ("green", "Barlow", "Oswald", "0.5rem", "0.375rem", "slideshow:slide:stripes", "brand", {
        "/services": ("services", "car serviced in garage mechanic", "Your services", "From booked to ready, with the bill."),
        "/book": ("book", "auto repair shop service bay", "Book a service", "Pick the work, pick a free day."),
        "/vehicles": ("vehicles", "cars parked row colorful", "Your vehicles", "Every car and bike in one place."),
        "/admin": ("admin", "mechanic workshop team tools", "The workshop today", "Bays, arrivals and takings."),
        "/admin/schedule": ("admin-schedule", "car lift garage repair", "Schedule", "Start, finish and cancel services."),
        "/admin/vehicles": ("admin-vehicles", "car engine inspection", "Vehicles", "Find any vehicle and its history."),
        "/admin/services": ("admin-services", "wrench tools garage wall", "Services", "What we offer and what it costs."),
    }),
    "appointment-booking": ("purple", "DM Sans", "Fraunces", "1rem", "0.75rem", "duotone:fade:stripes", "top", {
        "/providers": ("providers", "doctor consultation clinic friendly", "Find a provider", "Pick who you want to see."),
        "/appointments": ("appointments", "calendar planner desk appointment", "Your appointments", "Requests, confirmations and history."),
        "/provider": ("provider", "doctor clinic reception morning", "Your week", "Requests to answer and a calendar to fill."),
        "/provider/calendar": ("provider-calendar", "wall calendar schedule planning", "Calendar", "Open slots, bookings and gaps."),
        "/provider/appointments": ("provider-appointments", "clinic waiting room patients", "Appointments", "Confirm or cancel with a reason."),
        "/provider/profile": ("provider-profile", "professional portrait office", "Your profile", "What customers see about you."),
    }),
    "asset-allocation": ("stone", "Public Sans", "Space Grotesk", "0.375rem", "0.25rem", "mosaic:typewriter:stripes", "side", {
        "/my-assets": ("my-assets", "laptop and monitor workstation", "My equipment", "What IT has handed to you."),
        "/history": ("history", "returned equipment boxes shelf", "History", "Everything you have handed back."),
        "/admin": ("admin", "it department server room", "Equipment overview", "Where everything is."),
        "/admin/assets": ("admin-assets", "laptops stacked storage", "Assets", "Search, assign, repair, retire."),
        "/admin/returns": ("admin-returns", "technician checking laptop", "Returns", "Check equipment back in."),
    }),
    "food-delivery": ("brown", "Nunito", "Playfair Display", "1rem", "0.75rem", "kenburns:slide:stripes", "top", {
        "/menu": ("menu", "indian thali food overhead", "Today's menu", "Fresh from the tandoor."),
        "/orders": ("orders", "food delivery bag courier", "Your orders", "From the kitchen to your door."),
        "/admin": ("admin", "restaurant kitchen chefs busy", "The kitchen", "New, cooking, ready."),
        "/admin/menu": ("admin-menu", "restaurant menu spices ingredients", "The menu", "Switch dishes on and off."),
        "/admin/sales": ("admin-sales", "restaurant dining room evening", "Sales", "What sold, and when."),
    }),
    "library": ("amber", "Merriweather", "Lora", "0.75rem", "0.5rem", "slideshow:fade:curve", "top", {
        "/desk": ("desk", "librarian desk books", "At the desk", "Hand-overs, returns and overdue books."),
        "/desk/loans": ("desk-loans", "returning books library counter", "Reservations and loans", "Issue, take back, collect fines."),
        "/desk/books": ("desk-books", "bookshelf stacks library", "Books", "The catalogue and its copies."),
    }),
}


def write_look(project, layout, nav, banners):
    lines = ["export interface Banner {", "  image: string", "  title: string", "  text: string", "}", "",
             "/** How this app looks: the login arrangement, the navigation style and the photo banners shown above some pages. */",
             "export const look = {",
             f"  heroStyle: '{layout.split(':')[0]}' as 'slideshow' | 'kenburns' | 'duotone' | 'mosaic',",
             f"  headline: '{layout.split(':')[1]}' as 'rotate' | 'typewriter' | 'slide' | 'fade',",
             f"  edge: '{layout.split(':')[2]}' as 'wave' | 'diagonal' | 'stripes' | 'curve',",
             f"  navStyle: '{nav}' as 'top' | 'brand' | 'side',",
             "  banners: {"]
    for route, (name, _query, title, text) in banners.items():
        lines.append(f"    '{route}': {{ image: '/banner/{name}.jpg', title: {json.dumps(title)}, text: {json.dumps(text)} }},")
    lines += ["  } as Record<string, Banner>,", "}", ""]
    with open(os.path.join(ROOT, project, "frontend", "src", "look.ts"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))


def fetch_banners(project, banners):
    manifest = {"outDir": "frontend/public/banner", "size": "large2x", "images": {name: query for name, query, _t, _x in banners.values()}}
    path = os.path.join(ROOT, project, "images-look.json")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, indent=2)
    for attempt in range(4):  # the image service times out now and then; photos already saved are skipped
        done = subprocess.run([sys.executable, os.path.join(TOOLS, "fetch_images.py"), path], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=300)
        missing = [n for n in manifest["images"] if not os.path.exists(os.path.join(ROOT, project, "frontend", "public", "banner", n + ".jpg"))]
        if not missing:
            return
    print("  still missing:", missing)


def main():
    names = sys.argv[1:] or list(LOOKS)
    for project in names:
        palette, font, display, card, control, layout, nav, banners = LOOKS[project]
        apply_theme.apply(project, palette, font, display, card, control)
        for name in ("AuthShell.tsx", "Layout.tsx"):
            with open(os.path.join(TOOLS, "kit", name), encoding="utf-8") as src:
                text = src.read()
            with open(os.path.join(ROOT, project, "frontend", "src", "components", name), "w", encoding="utf-8", newline="\n") as dst:
                dst.write(text)
        write_look(project, layout, nav, banners)
        fetch_banners(project, banners)
        print(f"{project}: {layout} login, {nav} nav, {len(banners)} banners")


if __name__ == "__main__":
    main()
