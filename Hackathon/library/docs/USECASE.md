# Use case 16: Library Book Reservation and Issue System

Source: `cases/All_Cases.docx`

**Business Problem:** Students or employees need to search books, reserve available books and track issued/returned books.

**Users / Roles:** MEMBER, LIBRARIAN

**Core Features:**

- Member searches available books.
- Member reserves a book.
- Librarian views pending reservations.
- Librarian issues a reserved book.
- Member views currently issued books.
- Librarian records book return.

**Business Rules / Validation:** Unavailable books cannot be reserved; a member cannot reserve the same book twice while an active reservation exists; returned books become available.

**Optional Bonus:** Due-date calculation, overdue indicator, book category search.

**7-Hour Scope Guidance:** Teams should implement authentication, the core business workflow, JPA persistence, role-based authorization, REST APIs and a functional React UI first. Advanced features should only be attempted after the end-to-end flow works.

