"""python tools/readmes/library.py library/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Library Book Reservation and Issue System",
    "summary": "Members search the catalogue and reserve books; librarians hand them over, take them back, charge late fines and manage the "
               "stock. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8116, "frontend_port": 5116, "db": "library_desk", "package": "library",
    "logins": [("`librarian`", "`librarian123`", "LIBRARIAN"), ("`ramesh.pillai`", "`librarian123`", "LIBRARIAN"),
               ("`aarav.sharma` (one overdue book, one reservation)", "`member123`", "MEMBER"),
               ("`diya.patel`, `rohan.mehta`, … (25 members)", "`member123`", "MEMBER")],
    "features": [
        "**Member:** search the catalogue by title, author or ISBN, filter by category or \"only books I can reserve now\"; reserve a copy; "
        "cancel a reservation; a shelf with books on loan (due dates, overdue days and the fine so far), reservations waiting at the desk and a history.",
        "**Librarian:** a desk dashboard (books to hand over, on loan, overdue with the fine, fines collected, stock by category); a work list of "
        "reservations and loans with search; issue a reserved book (due in 14 days); take a book back (a late fine of 5 rupees a day is recorded); "
        "add books and change their details and number of copies.",
        "Due-date calculation, overdue indicator and category search (the case's bonus features).",
    ],
    "rules": [
        ("A book with no free copy cannot be reserved (reserved and issued copies are both held)", "`409`"),
        ("A member cannot reserve the same book twice while a reservation or loan of it is active", "`409`"),
        ("A member can hold at most 3 books (reserved or on loan) and cannot reserve while they have an overdue book", "`400`"),
        ("Returned and cancelled books free their copy straight away", "automatic"),
        ("Only a reservation can be cancelled, only by its owner; only a reservation can be issued; only an issued book can be returned", "`409` / `403`"),
        ("Due date is 14 days after issue; each day late costs 5 rupees, charged when the book is returned", "configurable in `application.properties`"),
        ("ISBN must be 10 or 13 digits and unique; the number of copies cannot be lowered below those reserved or on loan", "`400` field error"),
        ("Only librarians manage books and loans; only members reserve", "`403`"),
    ],
    "concurrency": "every change to a book's copies takes a row lock on the book (under `READ_COMMITTED`) and the member's own limit is counted "
                   "under a lock on the member, so when many members go for the last copy exactly one gets it, a double click reserves once, "
                   "five parallel reservations by one member stop at the limit of 3, and two desks cannot issue the same reservation. "
                   "The tests check all four with parallel threads.",
    "notes": [
        "Book covers are drawn by the front end (colour by category, title and author), so no cover images are stored.",
        "Availability is worked out from the loans table (total copies minus reserved and issued), never stored, so it cannot drift.",
    ],
    "api": [
        ("GET", "/api/books?search=&category=&availableOnly=", "any", "Catalogue with availability and own reservation"),
        ("GET", "/api/books/categories", "any", "Categories"),
        ("POST", "/api/loans", "MEMBER", "Reserve `{bookId}`"),
        ("GET", "/api/loans/my?status=", "MEMBER", "Own reservations and loans"),
        ("DELETE", "/api/loans/{id}", "MEMBER", "Cancel own reservation"),
        ("GET", "/api/librarian/loans?status=&search=", "LIBRARIAN", "All reservations and loans"),
        ("PUT", "/api/librarian/loans/{id}/issue", "LIBRARIAN", "Hand over a reserved book"),
        ("PUT", "/api/librarian/loans/{id}/return", "LIBRARIAN", "Take a book back (records any fine)"),
        ("POST", "/api/librarian/books", "LIBRARIAN", "Add a book `{title, author, isbn, category, description?, publishedYear?, totalCopies}`"),
        ("PUT", "/api/librarian/books/{id}", "LIBRARIAN", "Edit a book"),
        ("GET", "/api/librarian/dashboard", "LIBRARIAN", "Counts, overdue list, stock by category"),
    ],
    "controllers": "AuthController, BookController (member), LibrarianController",
    "services": "LibraryService (catalogue, reservations, issue and return, fines, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository (row lock), BookRepository (row lock), LoanRepository",
    "entities": "User, Book, Loan, Role, LoanStatus",
    "tables": "`users`, `books` (unique ISBN, total copies) and `loans` (book and member FKs, status, due date, fine).",
    "tests": "28 tests on in-memory H2 (no MySQL needed): search, reservation rules and limits, issue and return, fines, books, dashboard, roles, parallel reservations",
    "collection": "Library.postman_collection.json",
    "postman": "49 requests (74 assertions). It registers fresh members and adds a uniquely titled book each run, so it can be run many times",
    "limits": [
        "A reservation does not expire on its own; the librarian or the member cancels it.",
        "No renewals and no waiting queue for books that are all out.",
        "Searching and filtering is done in memory (Java Streams), which is fine for a college library but would need paging for a large catalogue.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
