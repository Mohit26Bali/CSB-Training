# Hackathon projects

One Spring Boot + React project per use case in `All_Cases.docx`. Every project has the same shape, and its own README with
the details: `backend/` (Spring Boot 4, JPA, MySQL, JWT), `frontend/` (React, TypeScript, Vite, Tailwind), `docs/postman/`
(a Postman collection) and `images.json` (where its photos came from).

| # | Folder | Case | Roles | Backend | Front end | Database |
|---|---|---|---|---|---|---|
| 13 | [`enrollment`](enrollment) | Online Course Enrollment and Approval | EMPLOYEE, ADMIN | 8080 | 5173 | `course_enrollment` |
| 11 | [`expense-management`](expense-management) | Smart Employee Expense Management | EMPLOYEE, MANAGER | 8111 | 5111 | `expense_management` |
| 12 | [`leave-portal`](leave-portal) | Leave Request and Approval Portal | EMPLOYEE, MANAGER | 8112 | 5112 | `leave_portal` |
| 19 | [`procurement`](procurement) | Simple Procurement Request System | EMPLOYEE, PROCUREMENT_MANAGER | 8119 | 5119 | `procurement_requests` |
| 5 | [`scholarship`](scholarship) | Scholarship Application Management | STUDENT, SCHOLARSHIP_ADMIN | 8105 | 5105 | `scholarship_mgmt` |
| 7 | [`warranty`](warranty) | Product Warranty Claim System | CUSTOMER, SERVICE_AGENT | 8107 | 5107 | `warranty_claims` |
| 9 | [`referral`](referral) | Internal Job Referral Portal | EMPLOYEE, RECRUITER | 8109 | 5109 | `job_referrals` |
| 18 | [`event-registration`](event-registration) | Event Registration and Approval | EMPLOYEE, EVENT_ADMIN | 8118 | 5118 | `event_registration` |
| 3 | [`hostel`](hostel) | Hostel Room Allocation | STUDENT, HOSTEL_ADMIN | 8103 | 5103 | `hostel_allocation` |
| 6 | [`parking`](parking) | Digital Parking Slot Management | EMPLOYEE, PARKING_ADMIN | 8106 | 5106 | `parking_slots` |
| 16 | [`library`](library) | Library Book Reservation and Issue | MEMBER, LIBRARIAN | 8116 | 5116 | `library_desk` |
| 8 | [`hotel-booking`](hotel-booking) | Hotel Room Booking | CUSTOMER, HOTEL_ADMIN | 8108 | 5108 | `hotel_booking` |
| 2 | [`vehicle-service`](vehicle-service) | Vehicle Service Booking | CUSTOMER, SERVICE_ADMIN | 8102 | 5102 | `vehicle_service` |
| 20 | [`appointment-booking`](appointment-booking) | Appointment Booking | CUSTOMER, SERVICE_PROVIDER | 8120 | 5120 | `appointment_booking` |
| 15 | [`asset-allocation`](asset-allocation) | Asset Allocation | EMPLOYEE, ASSET_ADMIN | 8115 | 5115 | `asset_allocation` |
| 1 | [`food-delivery`](food-delivery) | Food Delivery | CUSTOMER, RESTAURANT_ADMIN | 8101 | 5101 | `food_delivery` |
| 4 | [`visitors`](visitors) | Visitor Management | EMPLOYEE, SECURITY_ADMIN | 8104 | 5104 | `visitor_management` |
| 10 | [`maintenance`](maintenance) | Community Maintenance Requests | RESIDENT, FACILITY_MANAGER | 8110 | 5110 | `community_maintenance` |
| 14 | [`helpdesk`](helpdesk) | IT Help Desk Tickets | EMPLOYEE, SUPPORT_AGENT | 8114 | 5114 | `help_desk` |
| 17 | [`complaints`](complaints) | Customer Complaint Management | CUSTOMER, SUPPORT_AGENT | 8117 | 5117 | `complaint_portal` |

All 20 cases are built.

## Folder layout

```
Hackathon/
├── README.md                this index
├── cases/                   All_Cases.docx, the source of all 20 use cases
├── tools/                   shared generators: new project, images, Postman collections, READMEs
└── <project>/               one self-contained folder per case, all with the same shape:
    ├── backend/             Spring Boot app (pom.xml, mvnw, src/)
    ├── frontend/            React app (package.json, src/)
    ├── docs/USECASE.md      this project's use case text (from the docx)
    ├── docs/postman/        Postman collection
    ├── images.json         where the photos came from (enrollment: see its README)
    └── README.md            run steps, demo logins, rules, API list
```

## Run any project

Needs Java 17 (`JAVA_HOME` pointing at it), Node 20+, and MySQL 8 on `localhost:3306` (user `root`, password `mohit123`;
each database is created automatically).

```bash
cd <folder>/backend  && ./mvnw spring-boot:run        # PowerShell: .\mvnw spring-boot:run
cd <folder>/frontend && npm install && npm run dev    # first time; then just: npm run dev
```

Open `http://localhost:<front end port>`. The login page has buttons that fill in the demo accounts, and each project's README lists
every demo login. All projects can run at the same time because each has its own ports and database.

Backend tests need no MySQL (they use an in-memory database): `./mvnw test`.
API walkthrough: `postman collection run docs/postman/<name>.postman_collection.json` (backend running).

## Shared tooling (`tools/`)

| Tool | What it does |
|---|---|
| `new_project.py` | Creates a new project from the shared starter (JWT login, role-based security, error handling, React shell, dark mode, animated login page) |
| `apply_look.py` | Gives every project its own look (palette, fonts, corner roundness, login layout, navigation style, page photo banners). Edit its `LOOKS` table, then run it. Uses `tools/kit/` for the shared login shell and layout |
| `apply_theme.py` | The colour, font and radius part of the above, usable on its own |
| `usecase.py` | Writes a project's `docs/USECASE.md` from the docx |
| `fetch_images.py` | Downloads the Pexels photos listed in a project's `images.json` (needs `PEXELS_API_KEY`) |
| `postman_lib.py`, `collections/` | Build each project's Postman collection |
| `readme_lib.py`, `readmes/` | Build each project's README |
