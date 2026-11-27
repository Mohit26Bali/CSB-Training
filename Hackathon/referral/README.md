# Internal Job Referral Portal

Employees browse open positions and refer candidates; recruiters publish jobs, review referrals and move them through the hiring pipeline. Spring Boot REST API with JWT security, plus a React front end.

## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `job_referrals` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:8109)
cd backend
./mvnw spring-boot:run          # PowerShell: .\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:5109)
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:5109>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

| Username | Password | Role |
|---|---|---|
| `recruiter` | `recruiter123` | RECRUITER |
| `anil.kapoor` | `recruiter123` | RECRUITER |
| `aarav.sharma`, `diya.patel`, `rohan.mehta`, `ananya.iyer`, … (12 employees) | `employee123` | EMPLOYEE |

## Features

- **Employee:** browse open positions (search by title, team, location or skill, and filter by skill); refer a candidate with name, email, optional phone, resume link and a note; track every referral on a pipeline view with the recruiter's notes.
- **Recruiter:** create jobs as drafts, edit, publish, close (and reopen) them; review referrals by stage, job or text; move a referral one stage at a time (SUBMITTED → SCREENING → INTERVIEW → SELECTED) or reject it, with an optional note; a dashboard with referral counts per stage and per job, open jobs and hires.
- Search jobs by skill, a recruiter dashboard and referral counts (the case's bonus features).

## Business rules

| Rule | Result |
|---|---|
| Only PUBLISHED jobs accept referrals (drafts and closed jobs do not, and drafts are invisible to employees) | `400` |
| A candidate cannot be referred twice for the same job, even by a different employee (email compared ignoring case; also a unique constraint) | `409` |
| Only recruiters change a referral's status | `403` for employees |
| The pipeline moves one step at a time: no skipping, no repeating, no going back; REJECTED is possible from any unfinished stage | `409`, the message lists what is allowed |
| SELECTED and REJECTED are final | `409` |
| Closing a job stops new referrals; existing referrals can still be processed. A closed job can be reopened | `400` for new referrals |
| Employees see only their own referrals; recruiters cannot refer | `403` / filtered by user |
| Name and a valid email are mandatory; phone and resume link are validated when given | `400` with a field error each |

**Concurrency:** referring the same candidate from several tabs or by several employees at once creates one referral (the unique constraint settles the race and the losers get a clear `409`); every status change locks the referral row under `READ_COMMITTED`, so two recruiters cannot both advance the same referral. The tests check both with parallel threads.

Each referral response includes `allowedNext`, the statuses it can move to (the next stage first, `REJECTED` last), so the UI only offers legal moves.

## API

All endpoints except login and register need `Authorization: Bearer <token>`.

| Method | URL | Role | Purpose |
|---|---|---|---|
| POST | /api/auth/login | public | Log in, returns a token |
| POST | /api/auth/register | public | Create an account (always the lowest role) |
| GET | /api/auth/me | any | Current user |
| GET | /api/jobs?search=&skill= | any | Published jobs |
| GET | /api/jobs/skills | any | Skills used by published jobs |
| POST | /api/referrals | EMPLOYEE | Refer `{jobId, candidateName, candidateEmail, candidatePhone?, resumeUrl?, notes?}` |
| GET | /api/referrals/my?status= | EMPLOYEE | Own referrals |
| GET | /api/recruiter/jobs?status= | RECRUITER | All jobs with referral counts |
| POST | /api/recruiter/jobs | RECRUITER | Create a draft `{title, department, location, description, skills[]}` |
| PUT | /api/recruiter/jobs/{id} | RECRUITER | Edit a job |
| PUT | /api/recruiter/jobs/{id}/publish | RECRUITER | Publish (or reopen) a job |
| PUT | /api/recruiter/jobs/{id}/close | RECRUITER | Close a published job |
| GET | /api/recruiter/referrals?jobId=&status=&search= | RECRUITER | Referrals |
| PUT | /api/recruiter/referrals/{id}/status | RECRUITER | Move `{status, note?}` |
| GET | /api/recruiter/dashboard | RECRUITER | Counts per stage and per job |

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": { "field": "..." }, "timestamp": "..." }
```

## Architecture

```
React (5109) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/referral
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    AuthController, JobController (everyone), ReferralController (employee), RecruiterController
├── service       JobService, ReferralService (pipeline rules, dashboard), AuthService, LoginAttemptService
├── repo          UserRepository, JobRepository (row lock), ReferralRepository (row lock)
├── entity        User, Job, Referral, Role, JobStatus, ReferralStatus (knows its legal next stages)
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: `users`, `jobs` (status, skills) and `referrals` (job and referrer FKs, **UNIQUE (job, candidate email)**, status, recruiter note).

## Tests

```bash
cd backend
./mvnw test        # 25 tests on in-memory H2 (no MySQL needed): publishing rules, duplicates, the pipeline, search by skill, roles, dashboard, concurrency
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/Referral.postman_collection.json
```

It runs 49 requests (78 assertions). It registers a fresh employee and uses unique job titles and candidate emails, so it can be run many times. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero` come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

- Resumes are links, not uploads.
- There is no referral bonus tracking or notification email when a referral changes stage.
- Searching and filtering is done in memory (Java Streams), which is fine for one company's open roles but would need paging at scale.
- Login throttling is in memory, so it resets on restart.
- The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.
