# Hospital Appointment & Patient Management System (Day 09)

> **Day 09 version.** This builds on the Day 08 project (`Day 08/hospital-management`), which covers the whole core assignment. Day 09 adds the assignment's **optional industry-level extensions** (section 24):
> - Flyway database migrations
> - OpenAPI / Swagger UI
> - doctor working hours and leave
> - automatic available-slot generation
> - an audit table for status changes
> - a scheduled job that marks missed appointments as NO_SHOW
> - booking and cancellation notification events
> - caching of reference data
> - Docker Compose
> - a Testcontainers test
>
> They are described in [Day 09 extensions](#day-09-extensions). JWT authentication was **not** added (see [Not done](#not-done)).

A Spring Boot REST backend for a multi-specialty hospital.
- **Patients and doctors:** register patients, maintain departments and doctors.
- **Appointments:** book them without overlaps and move them through their lifecycle (BOOKED → CONFIRMED → COMPLETED / CANCELLED / NO_SHOW).
- **Medical records:** write one for each completed visit and show a patient's medical history.
- **Reports:** search, paginate and report on appointments.

The project demonstrates:
- entities and relationships
- derived queries, JPQL and DTO projections
- pagination and sorting
- validation and error handling
- transactions and rollback
- auditing
- optimistic and pessimistic locking
- fetch strategy
- (Day 09) database migrations, scheduled jobs, transactional events, caching and API documentation

It uses plain Spring Boot + Spring Data JPA. There is no Lombok, MapStruct or code generation: every class is written out.

## Tech

| | |
|---|---|
| Language / build | Java 17, Maven |
| Framework | Spring Boot 3.5.6 (Spring Web, Spring Data JPA, Bean Validation) |
| ORM | Hibernate 6.6 |
| Database | MySQL 8 (tested on 8.4), database **`hospital_day09`** (tests use a separate **`hospital_day09_test`**). Day 08's `hospital_management` database is not touched. |
| Migrations | **Flyway** (`src/main/resources/db/migration`, sample data in `db/sample`) |
| Caching | Spring Cache (in-memory `ConcurrentMapCacheManager`) |
| Tests | JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers |
| API documentation | **OpenAPI 3** via springdoc (`/swagger-ui.html`, exported to `docs/openapi.json`) and a Postman collection with tests in `postman/` |
| Containers | `Dockerfile` + `docker-compose.yml` (MySQL 8.4 + the application) |

## Project structure

```text
hospital-management/
├── pom.xml
├── Dockerfile, docker-compose.yml, .dockerignore      (Day 09) containers
├── docs/openapi.json                                  (Day 09) exported OpenAPI document
├── postman/hospital-management.postman_collection.json   60 requests, 133 assertions, re-runnable
└── src/
    ├── main/java/com/example/hospital/
    │   ├── HospitalApplication.java              @SpringBootApplication (run this)
    │   ├── config/                               JpaAuditingConfig; (Day 09) ClockConfig, CacheConfig,
    │   │                                         SchedulingConfig, OpenApiConfig
    │   ├── entity/                               Department, Doctor, Patient, Appointment, MedicalRecord
    │   │                                         + enums Gender, AppointmentStatus
    │   │                                         (Day 09) DoctorWorkingHours, DoctorLeave, AppointmentStatusChange
    │   ├── repository/                           Spring Data JPA interfaces (no implementations)
    │   ├── service/                              business rules + transaction boundaries
    │   │   ├── DepartmentService, DoctorService, PatientService
    │   │   ├── AppointmentService                booking, lifecycle, completion, status history, NO_SHOW marking
    │   │   ├── MedicalRecordService, ReportService
    │   │   ├── AppointmentNumberGenerator
    │   │   ├── DoctorScheduleService             (Day 09) working hours, leave, slot generation
    │   │   └── MissedAppointmentJob              (Day 09) @Scheduled NO_SHOW job
    │   ├── event/                                (Day 09) booking/status events + NotificationService
    │   ├── controller/                           REST controllers (HTTP only) incl. (Day 09) AdminController
    │   ├── dto/                                  request/response records (validation on requests)
    │   ├── mapper/                               entity -> response DTO (static methods)
    │   └── exception/                            domain exceptions + GlobalExceptionHandler
    ├── main/resources/
    │   ├── application.properties
    │   ├── db-secret.properties                  MySQL password (git-ignored; copy db-secret.properties.example)
    │   ├── db/migration/                         V1 schema, V2 working hours + leave, V3 status-change audit
    │   └── db/sample/                            V1_1 assignment sample data, V3_1 sample working hours
    ├── test/java/com/example/hospital/
    │   ├── TestFlywayConfig.java                 test database: Flyway clean + migrate before the tests
    │   ├── service/                              unit tests (Mockito)
    │   ├── controller/                           web-layer validation tests (MockMvc)
    │   └── integration/                          integration tests against MySQL (+ one Testcontainers test)
    └── test/resources/application.properties     test database settings
```

## How to run

### 1. The database

There is nothing to run by hand. On start-up:
1. The application connects to `hospital_day09` (`createDatabaseIfNotExist=true` creates it the first time).
2. **Flyway** runs every migration that has not run yet, in version order, and records each one in `flyway_schema_history`. On an empty database this applies 5 migrations: V1 tables, V1_1 sample data, V2 working-hours and leave tables, V3 audit table, V3_1 sample working hours.

To start again from the sample data, drop the database. The next start rebuilds it:

```text
mysql -u root -p -e "DROP DATABASE hospital_day09"
```

### 2. Start the application

In **Eclipse**:
1. **File → Import → Maven → Existing Maven Projects**, then select `hospital-management`.
2. Right-click `HospitalApplication` → **Run As → Java Application**.

From a terminal: `mvn spring-boot:run`, or `mvn package -DskipTests` then `java -jar target/hospital-management-0.0.1-SNAPSHOT.jar`.

The API runs on `http://localhost:8080`, and the Console shows every SQL statement Hibernate sends.

### 3. Explore and call the API

- **Swagger UI:** open `http://localhost:8080/swagger-ui.html` to see every endpoint with its request and response schemas, and to try them in the browser. The raw document is at `/v3/api-docs`, and a copy is in `docs/openapi.json`.
- **Postman:** **Import** `postman/hospital-management.postman_collection.json` and run the whole collection (Runner). From a terminal: `postman collection run postman/hospital-management.postman_collection.json`.
  - Dates are calculated from today, and every code includes a random run id, so the collection can be run again at any time **without resetting the database**.
  - Every request has `pm.test` checks.

### 4. Run the tests

In Eclipse, right-click `src/test/java` → **Run As → JUnit Test**. From a terminal, run `mvn test`.

The integration tests need MySQL running:
- They use their own database, `hospital_day09_test`, which Flyway wipes and rebuilds from the migrations at the start of every run, and never touch `hospital_day09`.
- `MySqlContainerIntegrationTest` also needs Docker; without it, the test is reported as **skipped**.

### 5. Docker (optional)

```text
docker compose up --build      # MySQL 8.4 + the application; API on http://localhost:8080
docker compose down -v         # stop and delete the database volume
```

- Optional: copy `.env.example` to `.env` to choose the MySQL root password; without it Compose uses the local demo password `hospital` (never reuse it).
- The compose file is validated (`docker compose config`) but the images were not built when this repo was prepared (the Docker daemon was off), so treat the first `up --build` as the real test.
- MySQL inside Compose is published on port **3307**, so it does not clash with a local MySQL on 3306.
- The application waits for MySQL's health check, then Flyway builds the schema and loads the sample data.

## REST API

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/departments` | create department |
| GET | `/api/departments` `[?activeOnly=true]` | list departments (all, or only active) |
| GET | `/api/departments/{id}` · `/api/departments/search?name=` | find by id / by name |
| PUT | `/api/departments/{id}` | update department |
| PATCH | `/api/departments/{id}/activate` · `/deactivate` | activate / deactivate |
| POST | `/api/doctors` | register doctor in a department |
| GET | `/api/doctors/{id}` | find doctor |
| PUT | `/api/doctors/{id}` | update details and consultation fee |
| PATCH | `/api/doctors/{id}/activate` · `/deactivate` | activate / deactivate |
| GET | `/api/doctors` `[?specialization=]` `[?departmentId=]` `[?minFee=&maxFee=]` | active doctors / search (one filter at a time) |
| GET | `/api/doctors/summaries` | lightweight list (JPQL DTO projection) |
| GET | `/api/doctors/available?specialization=&date=&startTime=&endTime=` | doctors free for a slot (Query 5) |
| GET | `/api/doctors/{id}/appointments?date=` | doctor schedule (Query 1) |
| POST | `/api/patients` | register patient |
| GET | `/api/patients/{id}` | find patient |
| PUT | `/api/patients/{id}` | update patient |
| PATCH | `/api/patients/{id}/deactivate` | deactivate (history is kept) |
| GET | `/api/patients/search?name=` · `?phone=` · `?email=` | search patients |
| GET | `/api/patients/{id}/history` | medical history (Query 2) |
| GET | `/api/patients/{id}/appointments` | all appointments, most recent first |
| POST | `/api/appointments` | book appointment |
| GET | `/api/appointments/{id}` | find appointment |
| GET | `/api/appointments?page=&size=&sort=` `[&doctorId=&patientId=&departmentId=&status=&date=]` | paginated search (Query 6) |
| PATCH | `/api/appointments/{id}/status` | change status (body: `status` + `version`) |
| POST | `/api/appointments/{id}/complete` | complete + create medical record in one transaction |
| POST | `/api/medical-records` | medical record for an already COMPLETED appointment |
| GET | `/api/reports/department-workload?departmentId=&fromDate=&toDate=` | Query 3 |
| GET | `/api/reports/revenue?fromDate=&toDate=` | Query 4 |
| GET / PUT | `/api/doctors/{id}/working-hours` | (Day 09) weekly timetable; PUT replaces it (`{"days":[...]}`) |
| GET / POST | `/api/doctors/{id}/leaves` | (Day 09) list / add leave (inclusive dates) |
| DELETE | `/api/doctors/{id}/leaves/{leaveId}` | (Day 09) remove a leave |
| GET | `/api/doctors/{id}/slots?date=` | (Day 09) generated slots: FREE / BOOKED / PAST |
| GET | `/api/appointments/{id}/status-history` | (Day 09) audit trail of status changes |
| POST | `/api/admin/no-show-check` | (Day 09) run the NO_SHOW job now |
| GET | `/api/admin/notifications` | (Day 09) notifications sent since start-up |
| GET | `/swagger-ui.html` · `/v3/api-docs` | (Day 09) OpenAPI UI and JSON |

Dates use the format `2026-10-20` and times `10:00`.

## Domain model and JPA relationships

```text
Department 1 ──── * Doctor 1 ──── * Appointment * ──── 1 Patient
                                        1
                                        │
                                       0..1
                                   MedicalRecord
```

| Relationship | Mapping | Foreign key (owning side) | Explanation |
|---|---|---|---|
| Department → Doctor | `Doctor.department`: `@ManyToOne(fetch = LAZY)` + `@JoinColumn(name = "department_id")`; `Department.doctors`: `@OneToMany(mappedBy = "department")` | `doctors.department_id` | A department has many doctors; each doctor belongs to exactly one. `Doctor` is the **owning side** because its table holds the FK. `mappedBy` marks the department's list as the read-only mirror. |
| Patient → Appointment | `Appointment.patient`: `@ManyToOne(LAZY)` + `@JoinColumn("patient_id")`; `Patient.appointments`: `@OneToMany(mappedBy)` | `appointments.patient_id` | One patient, many appointments. |
| Doctor → Appointment | `Appointment.doctor`: `@ManyToOne(LAZY)` + `@JoinColumn("doctor_id")`; `Doctor.appointments`: `@OneToMany(mappedBy)` | `appointments.doctor_id` | One doctor, many appointments. |
| Appointment → MedicalRecord | `MedicalRecord.appointment`: `@OneToOne(LAZY)` + `@JoinColumn("appointment_id", unique = true)` | `medical_records.appointment_id` (UNIQUE) | A completed appointment has at most one record; the UNIQUE FK enforces "one" in the database. It is mapped **only on the record side**: a `@OneToOne(mappedBy)` on `Appointment` would make Hibernate load the record every time an appointment is loaded. History queries join the record explicitly instead. |

**Every `@ManyToOne`/`@OneToOne` is LAZY.** JPA's default for them is EAGER. Queries that need the related data load it on purpose with `JOIN FETCH` or `@EntityGraph`.

**No collection is ever serialized:** controllers return DTOs, never entities.

### Other mapping features used

| Feature | Where | Purpose |
|---|---|---|
| `@Entity`, `@Table`, `@Id`, `@GeneratedValue(IDENTITY)` | all entities | class ↔ table, primary key generated by MySQL `AUTO_INCREMENT` |
| `@Column(nullable, unique, length, precision, scale)` | all entities | mirrors the database constraints |
| `@Enumerated(EnumType.STRING)` | `Patient.gender`, `Appointment.status` | stores `"CONFIRMED"`, not the position number |
| `@CreatedDate`, `@LastModifiedDate` + `@EntityListeners(AuditingEntityListener.class)` + `@EnableJpaAuditing` | `Appointment.createdAt/updatedAt` | **auditing:** set automatically on INSERT/UPDATE |
| `@Version` | `Appointment.version` | **optimistic locking** |
| `spring.jpa.hibernate.ddl-auto=validate` | `application.properties` | Hibernate checks that the entities match the tables Flyway created, at startup, but never changes the database |

## Business rules

### Booking workflow (`AppointmentService.bookAppointment`, one transaction)

1. Load the patient. Reject it if not found (404) or inactive (422 `PATIENT_INACTIVE`).
2. Load the doctor **and lock its row** (`SELECT ... FOR UPDATE`). Reject it if not found (404) or inactive (409 `DOCTOR_UNAVAILABLE`).
3. The date must not be in the past, and `endTime` must be after `startTime` (400).
   - **(Day 09)** The doctor must not be on leave that day. If the doctor has a timetable, the slot must also fit that weekday's working hours (409 `DOCTOR_UNAVAILABLE`).
4. **Overlap check:** reject the booking if the doctor already has an overlapping appointment (409 `DOCTOR_UNAVAILABLE`).
5. **Copy the doctor's current consultation fee** into `appointment.fee`.
6. Generate a unique appointment number (`APT-` + 8 random hex digits, protected by a UNIQUE constraint).
7. Save with status **BOOKED**.
   - **(Day 09)** In the same transaction, write the first audit row (`null → BOOKED`) and publish an `AppointmentBookedEvent`.
8. Return an `AppointmentResponse` DTO.

### The time-boundary rule (half-open intervals)

A slot is **`[startTime, endTime)`**: it includes the start minute and excludes the end. Two slots of the same doctor on the same date **overlap** when:

```text
existing.startTime < new.endTime   AND   existing.endTime > new.startTime
```

| Existing | New | Overlap? | Why |
|---|---|---|---|
| 10:00–10:30 | 10:15–10:45 | **yes, rejected** | 10:00 < 10:45 and 10:30 > 10:15 |
| 10:00–10:30 | 09:45–10:15 | **yes, rejected** | ends inside |
| 10:00–10:30 | 10:05–10:20 | **yes, rejected** | inside |
| 10:00–10:30 | 09:30–11:00 | **yes, rejected** | covers it |
| 10:00–10:30 | 10:00–10:30 | **yes, rejected** | same slot |
| 10:00–10:30 | **10:30–11:00** | **no, allowed** | 10:30 > 10:30 is false (back-to-back) |
| 10:00–10:30 | **09:30–10:00** | **no, allowed** | 10:00 < 10:00 is false |
| 10:00–10:30 | 11:00–11:30 | no, allowed | no contact |

Only **BOOKED, CONFIRMED and COMPLETED** appointments block a slot. **CANCELLED and NO_SHOW free it.** Every row of this table is an automated test (`AppointmentOverlapIntegrationTest`).

### Appointment lifecycle

| From | Allowed to |
|---|---|
| BOOKED | CONFIRMED, CANCELLED, NO_SHOW |
| CONFIRMED | COMPLETED, CANCELLED, NO_SHOW |
| COMPLETED, CANCELLED, NO_SHOW | nothing (final) |

Any other change gives 422 `INVALID_APPOINTMENT_STATUS`, so **a completed appointment cannot be cancelled**. A medical record can be created **only for a COMPLETED appointment**, and at most once.

### Why the fee is stored on the appointment

The fee is **copied** at booking time. In the demo below, D001's fee was raised from 800 to 1000:
- the existing appointment A10001 **still shows 800**, the price the patient was quoted
- a new booking got **1000**

If the fee were always read from `Doctor`, every price change would rewrite past bills and the revenue report.

## Spring Data JPA queries

### Derived queries (built from the method name)

| Method | Generated SQL (from the log) |
|---|---|
| `DoctorRepository.findBySpecializationIgnoreCase(String)` | `... from doctors d1_0 join departments d2_0 on ... where upper(d1_0.specialization)=upper(?)` |
| `DoctorRepository.findByDepartmentIdAndActiveTrue(Long)` | `... where d1_0.department_id=? and d1_0.active=1` |
| `DoctorRepository.findByConsultationFeeBetween(min, max)` | `... where d1_0.consultation_fee between ? and ?` |
| `PatientRepository.findByNameContainingIgnoreCaseOrderByNameAsc(String)` | `... where upper(p1_0.name) like upper(?) escape '\\' order by p1_0.name` |
| `PatientRepository.existsByPatientNumberIgnoreCase(String)` | `select p1_0.patient_id ... where upper(p1_0.patient_number)=upper(?) limit ?` |
| `AppointmentRepository.countByStatusAndAppointmentDateBetween(...)` | `select count(a1_0.appointment_id) ... where a1_0.status=? and a1_0.appointment_date between ? and ?` |
| `MedicalRecordRepository.existsByAppointmentId(Long)` | `select mr1_0.medical_record_id ... where a1_0.appointment_id=? limit ?` |

### Custom JPQL queries (`@Query`)

| # | Query | Method | Key idea |
|---|---|---|---|
| 1 | **Doctor schedule** | `AppointmentRepository.findDoctorSchedule` | `JOIN FETCH` patient, doctor and department, `ORDER BY a.startTime`. One SELECT, no N+1. |
| 2 | **Patient history** | `AppointmentRepository.findPatientHistory` | `SELECT new ...PatientHistoryItem(...)`: a DTO projection with `LEFT JOIN MedicalRecord mr ON mr.appointment = a`, COMPLETED only, newest first |
| 3 | **Department workload** | `AppointmentRepository.findDepartmentWorkload` | `SELECT new DoctorWorkload(d.id, ..., COUNT(a.id)) FROM Doctor d LEFT JOIN Appointment a ON ... GROUP BY ...`. Doctors with 0 appointments are kept. |
| 4 | **Revenue** | `AppointmentRepository.sumCompletedRevenue` | `SELECT COALESCE(SUM(a.fee), 0) ... WHERE status = COMPLETED AND date BETWEEN ...` |
| 5 | **Available doctors** | `DoctorRepository.findAvailableDoctors` | active doctors of a specialization `AND NOT EXISTS (overlapping appointment)` |
| 6 | **Search + pagination** | `AppointmentRepository.search(..., Pageable)` | every filter optional: `(:doctorId IS NULL OR d.id = :doctorId) AND ...`, with a separate `countQuery` |
| – | Overlap check | `AppointmentRepository.countOverlappingAppointments` | the half-open rule above |
| – | **Doctor summary** (section 16) | `DoctorRepository.findActiveDoctorSummaries` | `SELECT new com.example.hospital.dto.DoctorSummary(d.id, d.name, d.specialization, d.consultationFee) FROM Doctor d WHERE d.active = true ORDER BY d.name` |

For example, the generated SQL of **Query 2**. Only the 11 needed columns are read, and no entity is loaded:

```text
select a1_0.appointment_id,a1_0.appointment_number,a1_0.appointment_date,a1_0.start_time,d1_0.name,d1_0.specialization,
       a1_0.fee,mr1_0.medical_record_id,mr1_0.diagnosis,mr1_0.prescription,mr1_0.follow_up_date
from appointments a1_0 join doctors d1_0 on d1_0.doctor_id=a1_0.doctor_id
left join medical_records mr1_0 on mr1_0.appointment_id=a1_0.appointment_id
where a1_0.patient_id=? and a1_0.status='COMPLETED' order by a1_0.appointment_date desc,a1_0.start_time desc
```

### Pagination and sorting

`GET /api/appointments?page=0&size=10&sort=appointmentDate,desc`:
- Spring builds a `Pageable` from the parameters. The default is 10 per page, newest date first, with a maximum of 50 per page.
- The repository returns a `Page<Appointment>`.
- `AppointmentMapper.toPageResponse` turns it into:

```json
{"content":[ ... ],"page":0,"size":10,"totalElements":3,"totalPages":1,"first":true,"last":true,"sort":"appointmentDate: DESC"}
```

Only the fields `appointmentDate`, `startTime`, `status`, `fee`, `appointmentNumber` and `createdAt` can be sorted. Anything else is a 400 with the list of allowed fields.

### Fetch strategy and the N+1 problem

All `@ManyToOne`/`@OneToOne` associations are **LAZY**. A list query that then reads `doctor.getDepartment().getName()` for each row would run **1 + N** queries. The project avoids this in two ways:
- **`JOIN FETCH`** in JPQL (appointment queries): patient, doctor and department arrive in the same SELECT, as the SQL of the paginated list shows.
- **`@EntityGraph(attributePaths = "department")`** on derived doctor queries: Spring Data adds the JOIN (`... from doctors d1_0 join departments d2_0 ...`).

For reports, the **DTO projections** (`SELECT new ...`) read only the needed columns and load no entities at all. `spring.jpa.open-in-view=false` ensures no lazy loading happens accidentally while JSON is written: all data is loaded inside the service transaction.

## Transactions

Every service class is annotated `@Transactional(readOnly = true)`. Methods that change data override this with `@Transactional`. **The transaction boundary is the service method, never the controller or the repository**, because a business operation such as "check patient, check doctor, check overlap, insert" must see consistent data and succeed or fail as **one unit**.

| Method | Steps inside ONE transaction |
|---|---|
| `bookAppointment` | load patient, lock doctor, overlap check, insert |
| `changeStatus` | load, version check, transition check, update |
| `completeAppointment` | **status → COMPLETED (UPDATE) + INSERT medical record** |

### Rollback demonstration (`completeAppointment`)

Appointment 4 was CONFIRMED (version 1). It was then completed with an invalid follow-up date that comes **before** the visit:

```text
POST /api/appointments/4/complete  {"version":1,"diagnosis":"Migraine","followUpDate":"2026-10-01"}
-> 422 {"status":422,"errorCode":"INVALID_MEDICAL_RECORD","message":"followUpDate 2026-10-01 must be after the appointment date 2026-10-21",...}

SQL:
  select ... from appointments a1_0 join patients ... where a1_0.appointment_id=?
  update appointments set ...,status=?,updated_at=?,version=? where appointment_id=? and version=?     <- step 1 EXECUTED
  select mr1_0.medical_record_id from medical_records ... where a1_0.appointment_id=? limit ?        <- step 2 fails its rule

GET /api/appointments/4  ->  "status":"CONFIRMED", "version":1                                           <- rolled back
```

The status UPDATE really ran in MySQL, then the whole transaction was rolled back, so the appointment is still CONFIRMED and no medical record exists. This is also an automated test: `completeAppointment_whenRecordCreationFails_rollsBackTheStatusChange`.

## Concurrency

### Optimistic locking (`@Version` on Appointment)

Every UPDATE that Hibernate generates contains the version:

```text
update appointments set ...,status=?,updated_at=?,version=? where appointment_id=? and version=?
```

It increases the number by 1. A stale copy matches no row, and Hibernate throws instead of overwriting.

**Through the API.** The client must send back the `version` it loaded:

```text
GET   /api/appointments/1                                      -> "status":"BOOKED","version":0   (both receptionists)
PATCH /api/appointments/1/status {"status":"CONFIRMED","version":0}  -> 200, "version":1          (receptionist 1)
PATCH /api/appointments/1/status {"status":"CANCELLED","version":0}  -> 409
  {"status":409,"errorCode":"APPOINTMENT_MODIFIED","message":"Appointment A10001 was changed by another user (you sent version 0, current version is 1). Reload it and try again.",...}
GET   /api/appointments/1                                      -> "status":"CONFIRMED","version":1   (receptionist 1's change survived)
```

**At the JPA level.** `ConcurrencyIntegrationTest.optimisticLocking_preventsALostUpdate`:
1. It loads two copies of the same appointment (both version 0).
2. It saves copy A, so the version becomes 1.
3. It saves copy B, which throws **`ObjectOptimisticLockingFailureException`**. The handler maps this to 409 `CONCURRENT_UPDATE`.

The database keeps A's change.

### Two receptionists booking the same slot

Without protection, both bookings could run the overlap check **before** either has inserted, and both would succeed. `bookAppointment` prevents this:
- **Pessimistic row lock on the doctor:** `@Lock(PESSIMISTIC_WRITE)` gives `select ... from doctors d1_0 where d1_0.doctor_id=? for update`. A second booking for the same doctor waits until the first commits.
- **`isolation = READ_COMMITTED`:** after waiting, the second booking's overlap COUNT sees the row the first one just committed. With MySQL's default REPEATABLE READ, it would read an older snapshot and miss it.

The test `twoSimultaneousBookingsOfTheSameSlot_onlyOneSucceeds` starts two threads at the same moment. Exactly one gets **BOOKED** and the other **DOCTOR_UNAVAILABLE**, leaving 1 row in the table.

**When to use which:**
- **Optimistic locking** fits edits that rarely collide (status changes): no locks are held, and conflicts are detected afterwards.
- **A pessimistic lock** is used for booking, where two inserts must be serialized because there is no existing row whose version could clash.

## Day 09 extensions

### 1. Flyway database migrations

`schema.sql` and `sample-data.sql` were replaced by **versioned migrations**. Flyway applies them automatically at startup:

| File | Location | Content |
|---|---|---|
| `V1__initial_schema.sql` | `db/migration` | the 5 core tables (identical to Day 08's `schema.sql`) |
| `V1_1__sample_data.sql` | `db/sample` | the assignment's sample data (section 22) |
| `V2__doctor_working_hours_and_leave.sql` | `db/migration` | `doctor_working_hours`, `doctor_leaves` |
| `V3__appointment_status_changes.sql` | `db/migration` | `appointment_status_changes` |
| `V3_1__sample_schedules_and_history.sql` | `db/sample` | working hours for D001–D003, audit rows for A10001/A10002 |

- **Why migrations:**
  - Every database (a developer's, the test database, production, Docker) reaches exactly the same schema by running the same files in the same order.
  - `flyway_schema_history` records what ran, with a checksum, so an edited old migration is detected.
  - Changes are only ever added as new files.
- **Sample data:** it sits in a separate location (`db/sample`) that only `application.properties` includes, so the **test database gets the schema without the sample rows**.
- **Tests:** they run the same migrations. `TestFlywayConfig` wipes the test database first (Flyway `clean`, allowed only by the test properties), so every test run proves the migrations build a working schema from nothing. `ddl-auto=validate` then checks every entity against it.

### 2. OpenAPI documentation (springdoc)

- **What it shows:** `springdoc-openapi` reads the controllers and DTOs and generates the API description, including validation rules. For example, `@Min(5)` on `slotMinutes` becomes `"minimum": 5` in the schema.
- **Where to see it:**
  - Swagger UI: `/swagger-ui.html`
  - JSON: `/v3/api-docs`
  - an exported copy: `docs/openapi.json` (32 paths, 40 operations)
- **Annotations:** controllers are grouped with `@Tag`, and the new endpoints have `@Operation` summaries.

### 3. Doctor working hours, leave and available-slot generation

**New tables:**

| Table | Holds | Key constraint |
|---|---|---|
| `doctor_working_hours` | one row per doctor and weekday: start, end and slot length | UNIQUE (doctor, weekday) |
| `doctor_leaves` | inclusive date ranges | — |

Both are unidirectional `@ManyToOne(LAZY)` to `Doctor`, so loading a doctor never loads them.

**Rules (`DoctorScheduleService`):**

| Situation | Booking result |
|---|---|
| Doctor has **no timetable** | Any time is accepted (Day 08 behaviour, so existing data keeps working) |
| Doctor has a timetable, the weekday is not in it | 409 `does not work on SATURDAY` |
| Slot not inside that day's hours | 409 `works 09:00-12:00 on MONDAY; 11:45-12:15 is outside these hours` |
| Slot ends exactly at closing time (11:30–12:00) | Allowed: half-open intervals, like appointments |
| Doctor **on leave** that day (with or without timetable) | 409 `is on leave on ... (Conference, ...)` |

**Leave rules:**
- **Leaves cannot overlap** (409 `LEAVE_OVERLAP`). Two inclusive ranges overlap when `s1 <= e2 AND e1 >= s2`, expressed as a derived query `existsByDoctorIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual`.
- **Leave cannot be added over booked or confirmed appointments** (409 `LEAVE_HAS_APPOINTMENTS`). Cancel or move them first. A CANCELLED appointment does not block.
- **Locking:** `addLeave` takes the same doctor row lock (`SELECT ... FOR UPDATE`) as booking, so a booking and a leave for the same doctor cannot slip past each other's checks.

**Slot generation:** `GET /api/doctors/{id}/slots?date=`
1. It splits that weekday's hours into `slotMinutes` pieces.
2. It marks each piece:
   - **BOOKED** if a blocking appointment overlaps it (same half-open rule as booking)
   - **PAST** if it has already started (time from the injectable `Clock` bean)
   - **FREE** otherwise
3. When there are no slots, `dayStatus` says why: `ON_LEAVE`, `DAY_OFF`, `NO_TIMETABLE` or `INACTIVE`.

**Replacing a timetable** (`PUT /working-hours`) runs in one transaction:
1. One bulk `DELETE` (`@Modifying` JPQL).
2. The INSERTs, using `getReferenceById` so no `SELECT` of the doctor is needed.

### 4. Audit table for appointment status changes

**What's recorded:** every change writes one `appointment_status_changes` row (from, to, when, by whom, note):
- the booking itself (`null → BOOKED`)
- each status change through the API
- completion
- the NO_SHOW job

`changedAt` is filled by Spring Data auditing (`@CreatedDate`).

**Why it's in the same transaction:** a rolled-back operation leaves no audit row. The rollback demonstration of section 17 now also shows that the audit trail has no `COMPLETED` row (`AuditAndNotificationIntegrationTest.rolledBackCompletion_leavesNoAuditRow`).

**Where to read it:** `GET /api/appointments/{id}/status-history`, oldest first.

### 5. Scheduled job: missed appointments → NO_SHOW

**What it does:** `MissedAppointmentJob` runs `@Scheduled(cron = "0 15 * * * *")` (hourly). It marks every BOOKED or CONFIRMED appointment that ended more than the **grace period** (`hospital.no-show.grace-period=PT2H`) ago as **NO_SHOW**.
- It writes an audit row with `changedBy = NO_SHOW_JOB`.
- `POST /api/admin/no-show-check` runs it on demand.

**Transaction design:** the job class has **no** `@Transactional`.
1. It reads the matching ids.
2. It calls `AppointmentService.markNoShowIfStillOpen` once per appointment, so each one is updated in **its own transaction**.
3. That method re-checks the status, because a receptionist may have completed it in the meantime.
4. A concurrent change (optimistic lock failure) skips only that appointment. `MissedAppointmentJobTest` proves the others are still marked.

**Testing the timing:** the current time comes from a `Clock` bean, and the job's `run(now)` method takes the moment as a parameter. The tests check the exact boundary: an appointment ending 10:30 is **not** marked at 12:29, and **is** marked at 12:30.

**Turning it off:** scheduling is switched off with `hospital.no-show.enabled=false` (the tests do this).

### 6. Notification events (booking and cancellation)

**Publishing:** `AppointmentService` publishes `AppointmentBookedEvent` and `AppointmentStatusChangedEvent` with `ApplicationEventPublisher`. The events carry plain values, not entities: the listener runs after the transaction, when lazy fields can no longer be loaded.

**Listening:** `NotificationService` listens with **`@TransactionalEventListener(phase = AFTER_COMMIT)`**.
- **When it runs:** a notification is sent only if the transaction **commits**. `rolledBackBooking_sendsNoNotification` books inside an outer transaction that is rolled back: the event was published, but nothing was sent and no appointment exists.
- **Which events notify:** booking and cancellation. Confirmations and other changes are only in the audit trail.
- **Recipient:** the patient's e-mail, or "SMS + phone" when they have none.
- **Sending is simulated:** the message is written to the log and kept in memory (`GET /api/admin/notifications`).

### 7. Caching reference data

**Setup:** `@EnableCaching` with Spring's in-memory cache manager.

| Method | Annotation | Cache |
|---|---|---|
| `DepartmentService.findDepartments(activeOnly)` | `@Cacheable(key = "#activeOnly")` | `departments` |
| `DoctorService.findActiveDoctorSummaries()` | `@Cacheable` | `doctorSummaries` |
| every create / update / activate / deactivate of a department or doctor | `@CacheEvict(allEntries = true)` | the matching cache |

**Ordering:** the cache advice is ordered **outside** the transaction advice (`@EnableCaching(order = LOWEST_PRECEDENCE - 1)`), so eviction happens after the commit. A read in between therefore cannot put old data straight back into the cache.

**Proof:** `CachingIntegrationTest` shows the effect. A row inserted directly into the table is **not** seen while the cache is warm. A change made through the service empties the cache, and the next read sees everything.

**Appointments are not cached:** they change constantly and must always be current.

### 8. Docker and Testcontainers

**Dockerfile (multi-stage):**
- **Stage 1** builds the jar with Maven, caching the dependencies in their own layer.
- **Stage 2** runs it on a JRE as a non-root user.

The local password file is excluded by `.dockerignore`.

**docker-compose.yml:**
- MySQL 8.4 with a health check, a named volume, and port 3307 on the host.
- The application starts after MySQL is healthy, with the datasource set through environment variables.
- Flyway loads the schema and sample data.

**`MySqlContainerIntegrationTest`:**
- Starts a fresh `mysql:8.4` container (`@Testcontainers`, `@ServiceConnection` replaces the datasource).
- Checks that the 3 schema migrations ran on a clean server and that a booking works.
- `disabledWithoutDocker = true` reports it as skipped when Docker is not running.

## Day 09 evidence

**Fresh start** (empty `hospital_day09`, 25 October 2026):

```text
Migrating schema `hospital_day09` to version "1 - initial schema"
Migrating schema `hospital_day09` to version "1.1 - sample data"
Migrating schema `hospital_day09` to version "2 - doctor working hours and leave"
Migrating schema `hospital_day09` to version "3 - appointment status changes"
Migrating schema `hospital_day09` to version "3.1 - sample schedules and history"
Successfully applied 5 migrations to schema `hospital_day09`, now at version v3.1
```

**Slots** for D001 (sample timetable Mon–Sat 09:00–17:00, 30 minutes) on Monday 26 October:

```text
GET /api/doctors/1/slots?date=2026-10-26  ->  dayStatus WORKING, 09:00-17:00, 30-minute slots, 16 free of 16
```

**NO_SHOW job** on the sample data. A10001/A10002 were on 5 October and never completed:

```text
POST /api/admin/no-show-check
{"ranAt":"2026-10-25T18:42:42","cutoff":"2026-10-25T16:42:42","found":2,"markedNoShow":2,"skipped":0,
 "appointmentNumbers":["A10001","A10002"]}

GET /api/appointments/1/status-history
[{"fromStatus":null,"toStatus":"BOOKED","changedBy":"SAMPLE_DATA","note":"Booked",...},
 {"fromStatus":"BOOKED","toStatus":"NO_SHOW","changedBy":"NO_SHOW_JOB","note":"Not completed within 2 hour(s) after the end time",...}]
```

**Postman collection**, run twice in a row against the same database (`postman collection run`):

```text
|                requests |                  60 |                  0 |
|            test-scripts |                 120 |                  0 |
|              assertions |                 133 |                  0 |
```

It has 8 folders:
1. End-to-end flow
2. Overlap rule
3. Validation
4. Optimistic locking
5. Rollback
6. Search and reports
7. Working hours, leave and slots
8. Audit trail, notifications and the NO_SHOW job

A collection-level test also checks that **every error response** has `timestamp, status, errorCode, message, path`.

## Validation and error handling

**Validation:**
- **Request DTOs** use `jakarta.validation`: `@NotBlank`, `@NotNull`, `@Size`, `@Email`, `@Pattern` (10-digit phone), `@Positive` + `@Digits` (fee), `@Past` (date of birth) and `@FutureOrPresent` (appointment date).
- **The appointment request** also has `@AssertTrue isEndTimeAfterStartTime()`, which compares two fields.
- **The service repeats the date/time checks,** so the rules also hold for callers other than the REST API.

**Errors:** every error comes from `GlobalExceptionHandler` (`@RestControllerAdvice`) in the same shape:

```json
{"timestamp":"2026-10-11T22:09:39","status":409,"errorCode":"DUPLICATE_PATIENT","message":"A patient with number P1001 already exists","path":"/api/patients"}
```

| Exception category | Examples | HTTP |
|---|---|---|
| `NotFoundException` | `PatientNotFoundException`, `DoctorNotFoundException`, `AppointmentNotFoundException`, `DepartmentNotFoundException` | 404 |
| `ConflictException` | `DuplicatePatientException`, `DuplicateDoctorException`, `DuplicateDepartmentException`, `DoctorUnavailableException`, `MedicalRecordAlreadyExistsException`, `AppointmentModifiedException` | 409 |
| `BusinessRuleException` | `InvalidAppointmentStatusException`, `PatientInactiveException`, `DepartmentInactiveException`, `InvalidMedicalRecordException` | 422 |
| `BadRequestException` | `InvalidAppointmentTimeException`, `InvalidSearchCriteriaException` | 400 |
| Spring / database | `MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` (with `validationErrors`), unreadable JSON → 400 `MALFORMED_REQUEST`, `ObjectOptimisticLockingFailureException` → 409 `CONCURRENT_UPDATE`, `DataIntegrityViolationException` → 409 `DATA_INTEGRITY_VIOLATION` | |

The exceptions contain **no HTTP codes**: the service layer does not know about HTTP, and only the handler maps the categories.

## Database constraints

The Flyway migrations (`V1__initial_schema.sql`, plus V2 and V3 for the Day 09 tables) define:
- `UNIQUE` on `patient_number`, `doctor_code`, doctor `email`, `appointment_number`, department `name` and `medical_records.appointment_id`
- `FOREIGN KEY`s for all four relationships
- `NOT NULL` on every mandatory column
- `CHECK (consultation_fee > 0)` and `CHECK (end_time > start_time)`
- (Day 09):
  - `UNIQUE (doctor_id, day_of_week)` for working hours
  - `CHECK (slot_minutes BETWEEN 5 AND 240)`
  - `CHECK (end_date >= start_date)` for leave
  - foreign keys from all three new tables

The database rejects invalid rows **even without the application**. These results come from direct SQL on the demo database:

```text
INSERT INTO patients (patient_number, ...) VALUES ('P1001', ...)
ERROR 1062 (23000): Duplicate entry 'P1001' for key 'patients.uq_patients_patient_number'

INSERT INTO doctors (..., email, ...) VALUES (..., 'anil.kumar@hospital.com', ...)
ERROR 1062 (23000): Duplicate entry 'anil.kumar@hospital.com' for key 'doctors.uq_doctors_email'

INSERT INTO appointments (..., doctor_id, ...) VALUES (..., 999, ...)
ERROR 1452 (23000): Cannot add or update a child row: a foreign key constraint fails (... CONSTRAINT `fk_appointments_doctor` ...)

INSERT INTO appointments (..., start_time, end_time, ...) VALUES (..., '11:00', '10:30', ...)
ERROR 3819 (HY000): Check constraint 'chk_appointments_times' is violated.

INSERT INTO medical_records (appointment_id, diagnosis) VALUES (3, 'Duplicate')
ERROR 1062 (23000): Duplicate entry '3' for key 'medical_records.uq_medical_records_appointment'

INSERT INTO departments (name) VALUES (NULL)
ERROR 1048 (23000): Column 'name' cannot be null
```

**Why application checks alone are not enough:**
1. **Races between requests.** Two requests can both pass an `existsBy...` check before either inserts, and only a UNIQUE constraint stops the second.
2. **Other writers.** Scripts, other services or manual SQL bypass the Java code.
3. **Bugs.** A future code path may forget a check.

The application checks give friendly messages, and the constraints guarantee integrity. **Both are used.**

## Observed output: the end-to-end flow (recorded with the Day 08 version)

This was run on 11 October 2026 with the Day 08 Postman requests, in order, on a freshly reset database. The responses are abridged. The core behaviour is unchanged in Day 09. For the Day 09 run, see [Day 09 evidence](#day-09-evidence).

| # | Request | Status | Result |
|---|---|---|---|
| 1 | `POST /api/departments` `{"name":"Neurology","location":"Block D, Floor 2"}` | **201** | `{"id":5,"name":"Neurology","location":"Block D, Floor 2","active":true}` |
| 2 | `GET /api/departments?activeOnly=true` | 200 | 5 departments, sorted by name |
| 3 | `POST /api/doctors` `{"doctorCode":"D004","name":"Dr. Kavya Nair","specialization":"Neurology",...,"consultationFee":900.00,"departmentId":5}` | **201** | `{"id":4,"doctorCode":"D004",...,"departmentId":5,"departmentName":"Neurology"}` |
| 4 | `GET /api/doctors/4` | 200 | the same doctor |
| 5 | `GET /api/doctors?specialization=Cardiology` | 200 | `[{"id":1,"doctorCode":"D001","name":"Dr. Anil Kumar",...}]` |
| 6 | `POST /api/patients` `{"patientNumber":"P1004","name":"Rajiv Menon",...}` | **201** | `{"id":4,"patientNumber":"P1004",...,"active":true}` |
| 7 | `GET /api/patients/search?name=Raj` | 200 | Rajesh Kumar (P1001), Rajiv Menon (P1004) |
| 8 | `POST /api/appointments` `{"patientId":4,"doctorId":4,"appointmentDate":"2026-10-20","startTime":"10:00","endTime":"10:30","reason":"Recurring headaches"}` | **201** | `"appointmentNumber":"APT-C6C40F5C","status":"BOOKED","fee":900.00,"version":0` |
| 9 | `GET /api/appointments?page=0&size=10&sort=appointmentDate,desc` | 200 | 3 appointments, `"totalElements":3,"totalPages":1,"sort":"appointmentDate: DESC"` |
| 10 | `PATCH /api/appointments/3/status` `{"status":"CONFIRMED","version":0}` | 200 | `"status":"CONFIRMED","version":1` |
| 11 | `POST /api/appointments/3/complete` `{"version":1,"diagnosis":"Tension-type headache","prescription":"Paracetamol 500mg as needed","doctorNotes":"Reduce screen time","followUpDate":"2026-11-03"}` | 200 | `{"appointment":{...,"status":"COMPLETED","version":2,...},"medicalRecord":{"id":1,"appointmentId":3,"diagnosis":"Tension-type headache",...}}` |
| 12 | `GET /api/patients/4/history` | 200 | `[{"appointmentId":3,"appointmentNumber":"APT-C6C40F5C","appointmentDate":"2026-10-20","doctorName":"Dr. Kavya Nair","specialization":"Neurology","fee":900.00,"medicalRecordId":1,"diagnosis":"Tension-type headache","prescription":"Paracetamol 500mg as needed","followUpDate":"2026-11-03"}]` |
| 13 | `GET /api/doctors/4/appointments?date=2026-10-20` | 200 | the completed appointment |

Generated SQL of step 8, the booking transaction:

```text
select p1_0.patient_id,... from patients p1_0 where p1_0.patient_id=?
select d1_0.doctor_id,... from doctors d1_0 where d1_0.doctor_id=? for update                                   <- row lock
select count(a1_0.appointment_id) from appointments a1_0 where a1_0.doctor_id=? and a1_0.appointment_date=?
       and a1_0.status in (?,?,?) and a1_0.start_time<? and a1_0.end_time>?                                    <- overlap check
insert into appointments (appointment_date,appointment_number,created_at,doctor_id,end_time,fee,patient_id,reason,
       start_time,status,updated_at,version) values (?,?,?,?,?,?,?,?,?,?,?,?)
```

Database state after the full demo:

```text
| appointment_id | appointment_number | patient_id | doctor_id | appointment_date | start_time | end_time | status    | fee     | version |
|              1 | A10001             |          1 |         1 | 2026-10-05       | 10:00:00   | 10:30:00 | CONFIRMED |  800.00 |       1 |
|              2 | A10002             |          2 |         1 | 2026-10-05       | 10:30:00   | 11:00:00 | CONFIRMED |  800.00 |       0 |
|              3 | APT-C6C40F5C       |          4 |         4 | 2026-10-20       | 10:00:00   | 10:30:00 | COMPLETED |  900.00 |       2 |
|              4 | APT-D102F2D9       |          3 |         4 | 2026-10-21       | 10:00:00   | 10:30:00 | CONFIRMED |  900.00 |       1 |   <- rollback kept it CONFIRMED
|              5 | APT-F8DE3C4A       |          1 |         4 | 2026-10-21       | 10:30:00   | 11:00:00 | BOOKED    |  900.00 |       0 |   <- back-to-back slot allowed
|              6 | APT-DEA02F28       |          3 |         1 | 2026-10-23       | 09:00:00   | 09:30:00 | BOOKED    | 1000.00 |       0 |   <- new fee after the raise

| medical_record_id | appointment_id | diagnosis             | prescription                | doctor_notes       | follow_up_date |
|                 1 |              3 | Tension-type headache | Paracetamol 500mg as needed | Reduce screen time | 2026-11-03     |
```

## Failure and edge-case evidence (Day 08 run)

| Case | Request | Status | Response |
|---|---|---|---|
| Overlap | doctor 4, 2026-10-21 **10:15–10:45** (10:00–10:30 exists) | **409** | `DOCTOR_UNAVAILABLE`: `Doctor D004 already has an appointment overlapping 10:15-10:45 on 2026-10-21` |
| Boundary | doctor 4, 2026-10-21 **10:30–11:00** | **201** | booked (half-open rule) |
| Available doctors | `/api/doctors/available?specialization=Neurology&date=2026-10-21&startTime=10:15&endTime=10:45` | 200 | `[]` (and for 11:00–11:30: `[{"id":4,"name":"Dr. Kavya Nair",...}]`) |
| Past date | `"appointmentDate":"2026-10-01"` | **400** | `VALIDATION_FAILED`, `{"appointmentDate":"appointmentDate must not be in the past"}` |
| End before start | `"startTime":"11:00","endTime":"10:30"` | **400** | `VALIDATION_FAILED`, `{"endTimeAfterStartTime":"endTime must be after startTime"}` |
| Inactive doctor | D003 deactivated, then booked | **409** | `DOCTOR_UNAVAILABLE`: `Doctor D003 is not active` |
| Inactive patient | P1004 deactivated, then booked | **422** | `PATIENT_INACTIVE`: `Patient P1004 is inactive and cannot book appointments` (their history is still returned by `/history`) |
| Duplicate patient | `"patientNumber":"P1001"` | **409** | `DUPLICATE_PATIENT`: `A patient with number P1001 already exists` |
| Invalid patient | blank number/name, future birth date, bad email, 5-digit phone | **400** | 5 `validationErrors` at once |
| Negative fee | `"consultationFee":-100` | **400** | `{"consultationFee":"consultationFee must be greater than 0"}` |
| Invalid enum | `"gender":"UNKNOWN"` | **400** | `MALFORMED_REQUEST` |
| Unknown id | `GET /api/patients/99` | **404** | `PATIENT_NOT_FOUND`: `Patient not found with id 99` |
| Cancel completed | `{"status":"CANCELLED","version":2}` on appointment 3 | **422** | `INVALID_APPOINTMENT_STATUS`: `Appointment APT-C6C40F5C cannot change from COMPLETED to CANCELLED` |
| Skip a step | BOOKED → COMPLETED | **422** | `Appointment A10001 cannot change from BOOKED to COMPLETED` |
| Record twice | `POST /api/medical-records` for appointment 3 | **409** | `MEDICAL_RECORD_ALREADY_EXISTS`: `Appointment APT-C6C40F5C already has a medical record` |
| Record too early | `POST /api/medical-records` for BOOKED A10001 | **422** | `A medical record can only be created for a COMPLETED appointment; A10001 is BOOKED` |
| Stale version | second receptionist with version 0 | **409** | `APPOINTMENT_MODIFIED` (see Concurrency) |
| Invalid sort | `?sort=colour` | **400** | `INVALID_SEARCH_CRITERIA`: `Cannot sort by 'colour'. Allowed: [appointmentDate, startTime, status, fee, appointmentNumber, createdAt]` |
| Page 2 | `?doctorId=4&page=1&size=2&sort=startTime,asc` | 200 | 1 item, `"page":1,"size":2,"totalElements":3,"totalPages":2,"first":false,"last":true` |
| Workload | `/api/reports/department-workload?departmentId=5&fromDate=2026-10-01&toDate=2026-10-31` | 200 | `[{"doctorId":4,"doctorCode":"D004","doctorName":"Dr. Kavya Nair","appointmentCount":3}]` |
| Revenue | `/api/reports/revenue?fromDate=2026-10-01&toDate=2026-10-31` | 200 | `{"completedAppointments":1,"totalRevenue":900.00}` |

## Tests: 73 passing, 1 skipped

`mvn test` runs all of them. MySQL must be running; Docker is optional.

```text
Tests run: 74, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

The skipped test is `MySqlContainerIntegrationTest`, which needs Docker (see [Not done](#not-done)).

| Required test (section 21) | Test method |
|---|---|
| Create patient | `PatientServiceTest.createPatient_isPersisted`, `AppointmentWorkflowIntegrationTest.createPatient_isPersisted` |
| Duplicate patient number | `PatientServiceTest.duplicatePatientNumber_isRejected` |
| Create doctor with department | `DoctorServiceTest.createDoctorWithDepartment_linksTheDepartment`, `AppointmentWorkflowIntegrationTest.createDoctorWithDepartment_persistsTheRelationship` |
| Book valid appointment | `AppointmentServiceTest.bookValidAppointment_isCreatedAsBooked_withTheDoctorsFee` |
| Book overlapping appointment | `AppointmentServiceTest.bookOverlappingAppointment_isRejected` + 8 boundary cases in `AppointmentOverlapIntegrationTest.overlapRule` |
| Book inactive doctor | `AppointmentServiceTest.bookInactiveDoctor_isRejected` |
| Past appointment date | `AppointmentServiceTest.bookPastAppointmentDate_isRejected`, `AppointmentControllerTest.pastAppointmentDate_returns400` |
| Complete appointment | `AppointmentServiceTest.completeAppointment_changesStatus_andCreatesTheMedicalRecord`, `AppointmentWorkflowIntegrationTest.completeAppointment_changesStatus_createsRecord_andAppearsInHistory` |
| Create medical record twice | `MedicalRecordServiceTest.secondRecordForTheSameAppointment_isRejected`, `AppointmentWorkflowIntegrationTest.createMedicalRecordTwice_secondAttemptIsRejected` |
| Paginated search | `AppointmentWorkflowIntegrationTest.paginatedSearch_returnsTheRightPageAndMetadata` (12 rows, page 1 of size 5 → 5 items, 3 pages) |
| Concurrent update | `ConcurrencyIntegrationTest.optimisticLocking_preventsALostUpdate`, `secondReceptionist_withOldVersion_isRejected`, `twoSimultaneousBookingsOfTheSameSlot_onlyOneSucceeds` |
| Transaction rollback | `AppointmentWorkflowIntegrationTest.completeAppointment_whenRecordCreationFails_rollsBackTheStatusChange` |

**Day 09 tests (30 new):**

| Class | Tests | Proves |
|---|---|---|
| `DoctorScheduleIntegrationTest` | 13 | Bookings: any time without a timetable; inside / outside / closing-time boundary; day off; on leave (inclusive end). Leave: overlap, blocked by booked appointments but not cancelled ones. Timetables: duplicate weekday rejected, replacement removes old days. Slots: generated, BOOKED marked, back-to-back slot free, cancelled appointment frees its slot, every `dayStatus`, a past date is all PAST |
| `AuditAndNotificationIntegrationTest` | 5 | Audit trail order; rolled-back completion leaves no audit row; booking + cancellation notified after commit (SMS when there is no e-mail); confirmation not notified; rolled-back booking sends nothing |
| `MissedAppointmentJobIntegrationTest` | 3 | Exact grace-period boundary (12:29 no, 12:30 yes); BOOKED and CONFIRMED marked, COMPLETED and CANCELLED untouched; audit row written, and a second run changes nothing |
| `MissedAppointmentJobTest` (unit) | 1 | A concurrent change (optimistic lock failure) is skipped; the others are still marked |
| `CachingIntegrationTest` | 2 | Cached lists are reused; changes through the services evict them |
| `DoctorControllerTest` (web) | 3 | 400 for leave ending before it starts, for invalid working hours (two nested field errors), and for an unknown weekday |
| `OpenApiIntegrationTest` | 2 | `/v3/api-docs` lists the new endpoints and validation limits; Swagger UI is served |
| `MySqlContainerIntegrationTest` | 1 (skipped here) | Migrations on a fresh MySQL 8.4 container; booking works |

**Kinds of test:**

| Kind | Tests | What runs |
|---|---|---|
| **Unit** (`service/`) | 22 | Mockito mocks the repositories; no Spring, no database |
| **Web layer** (`controller/`) | 6 | `@WebMvcTest` + MockMvc: validation and the error JSON |
| **Integration** (`integration/`) | 46 | `@SpringBootTest` against real MySQL (`hospital_day09_test`), plus one Testcontainers test |

- **No `@Transactional` on the integration tests:** every operation commits for real, as in production.
- **Clean start for each test:** every table is emptied before each test, and the caches and in-memory notifications are cleared too.

## Trainer discussion questions: short answers

- **Why does the service define the transaction boundary?** A business operation spans several repository calls (check, check, insert), and only the service knows where the operation starts and ends. Controllers deal with HTTP, and a repository call is a single step.
- **Derived query vs JPQL vs native SQL:**
  - **Derived** for simple filters on one entity (`findBySpecializationIgnoreCase`).
  - **JPQL** when joins, grouping, `NOT EXISTS` or DTO projections are needed (Queries 1–6).
  - **Native** only for database-specific features JPQL cannot express. This project needs none.
- **Why LAZY?** EAGER loads related rows every time, even when unused, and causes extra queries for lists. LAZY plus an explicit `JOIN FETCH` or `@EntityGraph` loads exactly what each use case needs.
- **N+1:** 1 query for a list plus 1 per row for a lazy association. With 1000 appointments, that is 1001 queries. It is avoided here with `JOIN FETCH`, `@EntityGraph` and DTO projections.
- **A patient's history without unneeded collections:** a DTO projection query (`findPatientHistory`) that selects only the needed columns, with no `Patient.appointments` collection loaded.
- **Why store the fee on Appointment?** Historical correctness: the price at booking time must not change when the doctor's fee changes. This was demonstrated above (800 kept, 1000 for new bookings).
- **Two receptionists, same slot:** both can pass the overlap check before either inserts. It is solved with a doctor row lock + READ_COMMITTED, and proven by a two-thread test.
- **Where to enforce uniqueness?** **Both:** application checks for clear messages, and database constraints for guaranteed integrity under concurrency and outside the application.
- **Optimistic vs pessimistic locking:**
  - **Optimistic:** for conflicts that are rare (status edits). Nothing is locked, and stale writes are detected afterwards.
  - **Pessimistic:** when conflicts are likely or must be prevented up front (booking the same doctor).
- **Scaling to millions of appointments:**
  - the composite index `(doctor_id, appointment_date)`, which already exists
  - pagination everywhere, never unbounded lists
  - DTO projections for reports
  - read replicas for searches and reports
  - archiving or partitioning old appointments by date
  - caching reference data (departments, doctors)

## Notes

- **The sample appointments A10001 and A10002 use the numbering from the assignment.** New bookings use generated numbers such as `APT-C6C40F5C`.
- **Timestamp precision in the booking response:** the `createdAt`/`updatedAt` returned at booking time contain fractions of a second. The database column is `DATETIME`, so later reads show whole seconds.
- **Dates:** the Day 09 Postman collection computes its dates from today, so it does not go out of date. The sample appointments (5 October 2026) are in the past, so the NO_SHOW job marks them NO_SHOW the first time it runs, which is correct behaviour.
- **Flyway warns that MySQL 8.4 is newer than its tested range** ("latest supported version of MySQL is 8.1"). All migrations, the test runs and the demo worked on 8.4. A newer Flyway release can be used by overriding `flyway.version` in `pom.xml`.

## Not done

| Optional extension (section 24) | Status |
|---|---|
| JWT authentication and roles (ADMIN, RECEPTIONIST, DOCTOR, PATIENT) | **Not done.** It would change every endpoint, test and Postman request. The audit table's `changed_by` column is ready to hold the logged-in user instead of the channel (`API`, `NO_SHOW_JOB`). |
| Soft delete | Covered by the core design: patients, doctors and departments are **deactivated**, never deleted, so history is kept. |
| Docker Compose / Testcontainers | Written and validated with `docker compose config`, but **not run here**: the Docker engine was not running on this computer. The Testcontainers test is reported as skipped for the same reason. |

