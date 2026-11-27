"""python tools/collections/library.py library/docs/postman/Library.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
const digits = (n) => Array.from({ length: n }, () => Math.floor(Math.random() * 10)).join("");
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("isbn", "979" + digits(10));
pm.collectionVariables.set("title", "Postman Handbook " + id);
"""


def book(copies=1, title="{{title}}", isbn="{{isbn}}", category="Postman"):
    return {"title": title, "author": "Pat Man", "isbn": isbn, "category": category, "description": "Created by the Postman run",
            "publishedYear": 2024, "totalCopies": copies}


items = [
    folder("1. Auth", [
        req("Login as librarian", "POST", "/api/auth/login", body={"username": "librarian", "password": "librarian123"},
            tests=login_tests("librarianToken"), pre=PRE),
        req("Register a fresh member for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Member", "role": "LIBRARIAN"}, tests=f"""
{status(201)}
pm.test("Always a member", () => pm.expect(pm.response.json().role).to.eql("MEMBER"));
pm.collectionVariables.set("memberToken", pm.response.json().token);
"""),
        req("Register a second member", "POST", "/api/auth/register",
            body={"username": "{{newUser}}b", "password": "password123", "fullname": "Postman Member B"}, tests=f"""
{status(201)}
pm.collectionVariables.set("member2Token", pm.response.json().token);
"""),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "librarian", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Librarian adds a book", [
        req("Empty form lists every problem", "POST", "/api/librarian/books", token="librarianToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("title", "author", "isbn", "category", "totalCopies"));
"""),
        req("A malformed ISBN is rejected", "POST", "/api/librarian/books", token="librarianToken", body=book(isbn="12345"), tests=f"""
{status(400)}
pm.test("ISBN error", () => pm.expect(pm.response.json().fieldErrors.isbn).to.include("valid 10 or 13 digit"));
"""),
        req("Zero copies is rejected", "POST", "/api/librarian/books", token="librarianToken", body=book(copies=0), tests=f"""
{status(400)}
pm.test("Copies error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("totalCopies"));
"""),
        req("Add a book with one copy (valid 13-digit ISBN)", "POST", "/api/librarian/books", token="librarianToken",
            body=book(isbn="{{isbn}}"), tests=f"""
{status(201)}
const b = pm.response.json();
pm.test("One copy, all available", () => {{ pm.expect(b.totalCopies).to.eql(1); pm.expect(b.availableCopies).to.eql(1); pm.expect(b.isbn).to.eql(pm.collectionVariables.get("isbn")); }});
pm.collectionVariables.set("bookId", b.id);
"""),
        req("The same ISBN again is rejected", "POST", "/api/librarian/books", token="librarianToken", body=book(title="Another"), tests=f"""
{status(400)}
pm.test("Duplicate ISBN", () => pm.expect(pm.response.json().fieldErrors.isbn).to.include("already exists"));
"""),
    ]),
    folder("3. Member searches and reserves", [
        req("Search by title", "GET", "/api/books?search={{runId}}", token="memberToken", tests=f"""
{status(200)}
pm.test("Found with its single copy", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].availableCopies).to.eql(1); pm.expect(l[0].myLoanStatus).to.eql(null); }});
"""),
        req("Search by ISBN", "GET", "/api/books?search={{isbn}}", token="memberToken", tests=f"""
{status(200)}
pm.test("Found by ISBN", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Filter by category", "GET", "/api/books?category=postman&search={{runId}}", token="memberToken", tests=f"""
{status(200)}
pm.test("Category filter ignores case", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Missing book is reported", "POST", "/api/loans", token="memberToken", body={}, tests=f"""
{status(400)}
pm.test("Field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("bookId"));
"""),
        req("Unknown book is reported", "POST", "/api/loans", token="memberToken", body={"bookId": 999999}, tests=status(400)),
        req("Reserve the book", "POST", "/api/loans", token="memberToken", body={"bookId": "{{bookId}}"}, tests=f"""
{status(201)}
const l = pm.response.json();
pm.test("Reserved", () => pm.expect(l.status).to.eql("RESERVED"));
pm.collectionVariables.set("loanId", l.id);
"""),
        req("Reserving the same book twice is a conflict", "POST", "/api/loans", token="memberToken", body={"bookId": "{{bookId}}"}, tests=status(409)),
        req("The only copy is gone for member B", "POST", "/api/loans", token="member2Token", body={"bookId": "{{bookId}}"}, tests=f"""
{status(409)}
pm.test("Says not available", () => pm.expect(pm.response.json().message).to.include("not available"));
"""),
        req("Available-only search hides it", "GET", "/api/books?search={{runId}}&availableOnly=true", token="member2Token", tests=f"""
{status(200)}
pm.test("Hidden", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
        req("The member sees their own reservation in search", "GET", "/api/books?search={{runId}}", token="memberToken", tests=f"""
{status(200)}
pm.test("Marked as mine", () => {{ const b = pm.response.json()[0]; pm.expect(b.myLoanStatus).to.eql("RESERVED"); pm.expect(b.availableCopies).to.eql(0); }});
"""),
    ]),
    folder("4. Librarian issues and takes it back", [
        req("The reservation waits at the desk", "GET", "/api/librarian/loans?status=RESERVED&search={{runId}}", token="librarianToken", tests=f"""
{status(200)}
pm.test("One, with the member's name", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].memberName).to.eql("Postman Member"); }});
"""),
        req("A reserved book cannot be returned yet", "PUT", "/api/librarian/loans/{{loanId}}/return", token="librarianToken", tests=status(409)),
        req("Issue it", "PUT", "/api/librarian/loans/{{loanId}}/issue", token="librarianToken", tests=f"""
{status(200)}
pm.test("Issued with a due date two weeks away", () => {{ const l = pm.response.json(); pm.expect(l.status).to.eql("ISSUED"); pm.expect(l.dueDate).to.be.a("string"); pm.expect(l.overdue).to.be.false; pm.expect(l.issuedBy).to.eql("librarian"); }});
"""),
        req("Issuing again is a conflict", "PUT", "/api/librarian/loans/{{loanId}}/issue", token="librarianToken", tests=status(409)),
        req("A member cannot cancel an issued book", "DELETE", "/api/loans/{{loanId}}", token="memberToken", tests=status(409)),
        req("Unknown loan is 404", "PUT", "/api/librarian/loans/999999/issue", token="librarianToken", tests=status(404)),
        req("The member sees the book on loan", "GET", "/api/loans/my?status=ISSUED", token="memberToken", tests=f"""
{status(200)}
pm.test("One issued loan", () => pm.expect(pm.response.json().some(l => l.id == pm.collectionVariables.get("loanId"))).to.be.true);
"""),
        req("Reserving an issued book is still a conflict", "POST", "/api/loans", token="memberToken", body={"bookId": "{{bookId}}"}, tests=status(409)),
        req("Take the book back", "PUT", "/api/librarian/loans/{{loanId}}/return", token="librarianToken", tests=f"""
{status(200)}
pm.test("Returned on time, no fine", () => {{ const l = pm.response.json(); pm.expect(l.status).to.eql("RETURNED"); pm.expect(l.fine).to.eql(0); }});
"""),
        req("Returning again is a conflict", "PUT", "/api/librarian/loans/{{loanId}}/return", token="librarianToken", tests=status(409)),
        req("The copy is back on the shelf", "GET", "/api/books?search={{runId}}", token="member2Token", tests=f"""
{status(200)}
pm.test("Available again", () => pm.expect(pm.response.json()[0].availableCopies).to.eql(1));
"""),
    ]),
    folder("5. Cancelling and the copy count", [
        req("Member B reserves it now", "POST", "/api/loans", token="member2Token", body={"bookId": "{{bookId}}"}, tests=f"""
{status(201)}
pm.collectionVariables.set("loan2Id", pm.response.json().id);
"""),
        req("Member A cannot cancel B's reservation", "DELETE", "/api/loans/{{loan2Id}}", token="memberToken", tests=status(403)),
        req("Unknown reservation is 404", "DELETE", "/api/loans/999999", token="member2Token", tests=status(404)),
        req("Member B cancels", "DELETE", "/api/loans/{{loan2Id}}", token="member2Token", tests=f"""
{status(200)}
pm.test("Cancelled", () => pm.expect(pm.response.json().status).to.eql("CANCELLED"));
"""),
        req("Cancelling again is a conflict", "DELETE", "/api/loans/{{loan2Id}}", token="member2Token", tests=status(409)),
        req("A cancelled reservation cannot be issued", "PUT", "/api/librarian/loans/{{loan2Id}}/issue", token="librarianToken", tests=status(409)),
        req("Member A may borrow it again after returning", "POST", "/api/loans", token="memberToken", body={"bookId": "{{bookId}}"}, tests=f"""
{status(201)}
pm.collectionVariables.set("loan3Id", pm.response.json().id);
"""),
    ]),
    folder("6. Copies and the dashboard", [
        req("Raise the copies to 3", "PUT", "/api/librarian/books/{{bookId}}", token="librarianToken", body=book(copies=3), tests=f"""
{status(200)}
pm.test("Two available while one is reserved", () => pm.expect(pm.response.json().availableCopies).to.eql(2));
"""),
        req("Member B reserves a second copy", "POST", "/api/loans", token="member2Token", body={"bookId": "{{bookId}}"}, tests=status(201)),
        req("Copies cannot drop below those in use", "PUT", "/api/librarian/books/{{bookId}}", token="librarianToken", body=book(copies=1), tests=f"""
{status(400)}
pm.test("Copies error", () => pm.expect(pm.response.json().fieldErrors.totalCopies).to.include("reserved or on loan"));
"""),
        req("Dashboard", "GET", "/api/librarian/dashboard", token="librarianToken", tests=f"""
{status(200)}
pm.test("Totals, categories and overdue list", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("titles", "copies", "available", "reserved", "issued", "overdue", "finesCollected", "byCategory", "overdueLoans");
  pm.expect(d.byCategory.some(c => c.category == "Postman")).to.be.true;
}});
"""),
        req("Loan list can be filtered by member", "GET", "/api/librarian/loans?search={{newUser}}", token="librarianToken", tests=f"""
{status(200)}
pm.test("Only this run's members", () => pm.expect(pm.response.json().length).to.be.at.least(3));
"""),
    ]),
    folder("7. Security checks", [
        req("No token is 401", "GET", "/api/books", tests=status(401)),
        req("Member cannot see the librarian's loans", "GET", "/api/librarian/loans", token="memberToken", tests=status(403)),
        req("Member cannot add books", "POST", "/api/librarian/books", token="memberToken", body=book(), tests=status(403)),
        req("Member cannot issue", "PUT", "/api/librarian/loans/{{loan3Id}}/issue", token="memberToken", tests=status(403)),
        req("Member cannot see the dashboard", "GET", "/api/librarian/dashboard", token="memberToken", tests=status(403)),
        req("Librarian cannot reserve", "POST", "/api/loans", token="librarianToken", body={"bookId": "{{bookId}}"}, tests=status(403)),
        req("Librarian has no personal loans", "GET", "/api/loans/my", token="librarianToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Library Reservation and Issue API",
    "Walks the library workflow: catalogue search, reservation limits and duplicates, issuing, returns and fines, cancelling, copy counts, the dashboard and security.\n"
    "Start the backend first (http://localhost:8116). Repeatable: it registers fresh members and adds a uniquely titled book with a random ISBN each run.",
    items, {"baseUrl": "http://localhost:8116", **{k: "" for k in [
        "librarianToken", "memberToken", "member2Token", "bookId", "loanId", "loan2Id", "loan3Id", "runId", "newUser", "isbn", "title"]}})
