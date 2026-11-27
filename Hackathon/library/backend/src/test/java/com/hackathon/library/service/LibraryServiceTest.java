package com.hackathon.library.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.hackathon.library.dto.BookBody;
import com.hackathon.library.dto.BookResponse;
import com.hackathon.library.dto.LibraryDashboard;
import com.hackathon.library.dto.LoanResponse;
import com.hackathon.library.entity.Book;
import com.hackathon.library.entity.Loan;
import com.hackathon.library.entity.LoanStatus;
import com.hackathon.library.entity.Role;
import com.hackathon.library.entity.User;
import com.hackathon.library.exception.BusinessRuleException;
import com.hackathon.library.exception.ConflictException;
import com.hackathon.library.exception.FieldValidationException;
import com.hackathon.library.exception.ForbiddenActionException;
import com.hackathon.library.exception.InvalidStatusException;
import com.hackathon.library.repo.BookRepository;
import com.hackathon.library.repo.LoanRepository;
import com.hackathon.library.repo.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class LibraryServiceTest {

    @Autowired LibraryService library;
    @Autowired BookRepository books;
    @Autowired LoanRepository loans;
    @Autowired UserRepository users;

    Book java;
    Book single;
    Book sapiens;
    Book atomic;
    Book dune;

    @BeforeEach
    void setUp() {
        loans.deleteAll();
        books.deleteAll();
        users.deleteAll();
        users.save(new User("lib", "x", "Librarian", Role.LIBRARIAN));
        for (int i = 1; i <= 6; i++) {
            users.save(new User("m" + i, "x", "Member " + i, Role.MEMBER));
        }
        java = books.save(new Book("Effective Java", "Joshua Bloch", "9780134685991", "Technology", "Java tips", 2018, 2));
        single = books.save(new Book("The Pragmatic Programmer", "Hunt & Thomas", "9780135957059", "Technology", null, 1999, 1));
        sapiens = books.save(new Book("Sapiens", "Yuval Noah Harari", "9780062316097", "History", null, 2011, 3));
        atomic = books.save(new Book("Atomic Habits", "James Clear", "9780735211292", "Self-help", null, 2018, 3));
        dune = books.save(new Book("Dune", "Frank Herbert", "9780441172719", "Fiction", null, 1965, 3));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    LoanResponse reserve(String member, Book book) {
        loginAs(member, Role.MEMBER);
        return library.reserve(member, book.getId());
    }

    LoanResponse issue(LoanResponse loan) {
        loginAs("lib", Role.LIBRARIAN);
        return library.issue(loan.id(), "lib");
    }

    LoanResponse giveBack(LoanResponse loan) {
        loginAs("lib", Role.LIBRARIAN);
        return library.giveBack(loan.id(), "lib");
    }

    /** Pushes an issued loan's due date into the past by editing it directly. */
    void makeOverdue(LoanResponse loan, int days) {
        Loan stored = loans.findById(loan.id()).orElseThrow();
        stored.issue("lib", LocalDate.now().minusDays(days));
        loans.save(stored);
    }

    // ---------- searching ----------

    @Test
    void searchByTitleAuthorIsbnCategoryAndAvailability() {
        loginAs("m1", Role.MEMBER);
        assertEquals(5, library.search("m1", null, null, false).size());
        assertEquals(List.of("Effective Java"), library.search("m1", "bloch", null, false).stream().map(BookResponse::title).toList());
        assertEquals(1, library.search("m1", "978-0-06-231609-7", null, false).size());
        assertEquals(1, library.search("m1", null, "history", false).size());
        reserve("m2", single);
        assertEquals(4, library.search("m1", null, null, true).size()); // the single copy is gone
        assertEquals(List.of("Technology", "Fiction", "History", "Self-help").stream().sorted().toList(), library.categories());
    }

    @Test
    void searchShowsTheMembersOwnReservation() {
        LoanResponse r = reserve("m1", java);
        BookResponse mine = library.search("m1", "effective", null, false).get(0);
        assertEquals("RESERVED", mine.myLoanStatus());
        assertEquals(r.id(), mine.myLoanId());
        assertEquals(1, mine.availableCopies());
        loginAs("m2", Role.MEMBER);
        assertNull(library.search("m2", "effective", null, false).get(0).myLoanStatus());
    }

    // ---------- reserving ----------

    @Test
    void reserveHoldsACopyUntilCancelled() {
        LoanResponse r = reserve("m1", single);
        assertEquals("RESERVED", r.status());
        ConflictException full = assertThrows(ConflictException.class, () -> reserve("m2", single));
        assertTrue(full.getMessage().contains("not available"));
        loginAs("m1", Role.MEMBER);
        assertEquals("CANCELLED", library.cancel("m1", r.id()).status());
        assertEquals("RESERVED", reserve("m2", single).status());
    }

    @Test
    void sameBookCannotBeReservedTwiceWhileReservedOrOnLoan() {
        LoanResponse r = reserve("m1", java);
        assertThrows(ConflictException.class, () -> reserve("m1", java));
        issue(r);
        assertThrows(ConflictException.class, () -> reserve("m1", java));
        giveBack(r);
        assertEquals("RESERVED", reserve("m1", java).status()); // allowed again after the return
    }

    @Test
    void unknownBookIsAFieldError() {
        loginAs("m1", Role.MEMBER);
        FieldValidationException ex = assertThrows(FieldValidationException.class, () -> library.reserve("m1", 9999L));
        assertEquals("bookId", ex.getField());
    }

    @Test
    void memberCanHoldAtMostThreeBooks() {
        reserve("m1", java);
        reserve("m1", sapiens);
        reserve("m1", atomic);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> reserve("m1", dune));
        assertTrue(ex.getMessage().contains("at most 3"));
    }

    @Test
    void overdueBookBlocksNewReservations() {
        LoanResponse r = issue(reserve("m1", java));
        makeOverdue(r, 3);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> reserve("m1", sapiens));
        assertTrue(ex.getMessage().contains("overdue"));
        giveBack(r);
        assertEquals("RESERVED", reserve("m1", sapiens).status());
    }

    @Test
    void onlyTheOwnerCancelsAndOnlyReservations() {
        LoanResponse r = reserve("m1", java);
        loginAs("m2", Role.MEMBER);
        assertThrows(ForbiddenActionException.class, () -> library.cancel("m2", r.id()));
        issue(r);
        loginAs("m1", Role.MEMBER);
        assertThrows(InvalidStatusException.class, () -> library.cancel("m1", r.id()));
    }

    // ---------- librarian ----------

    @Test
    void issueSetsTheDueDateAndMemberSeesIt() {
        LoanResponse issued = issue(reserve("m1", java));
        assertEquals("ISSUED", issued.status());
        assertEquals(LocalDate.now().plusDays(14), issued.dueDate());
        assertEquals("lib", issued.issuedBy());
        assertFalse(issued.overdue());
        loginAs("m1", Role.MEMBER);
        assertEquals(1, library.myLoans("m1", LoanStatus.ISSUED).size());
        assertEquals(0, library.myLoans("m1", LoanStatus.RESERVED).size());
    }

    @Test
    void issuingTwiceOrIssuingACancelledReservationFails() {
        LoanResponse r = reserve("m1", java);
        issue(r);
        loginAs("lib", Role.LIBRARIAN);
        assertThrows(InvalidStatusException.class, () -> library.issue(r.id(), "lib"));
        LoanResponse cancelled = reserve("m2", java);
        loginAs("m2", Role.MEMBER);
        library.cancel("m2", cancelled.id());
        loginAs("lib", Role.LIBRARIAN);
        assertThrows(InvalidStatusException.class, () -> library.issue(cancelled.id(), "lib"));
    }

    @Test
    void returnFreesTheCopyAndOnlyWorksOnceOnIssuedBooks() {
        LoanResponse r = reserve("m1", single);
        loginAs("lib", Role.LIBRARIAN);
        assertThrows(InvalidStatusException.class, () -> library.giveBack(r.id(), "lib")); // not issued yet
        issue(r);
        LoanResponse back = giveBack(r);
        assertEquals("RETURNED", back.status());
        assertEquals(0, back.fine());
        assertThrows(InvalidStatusException.class, () -> library.giveBack(r.id(), "lib"));
        assertEquals("RESERVED", reserve("m2", single).status()); // the copy is available again
    }

    @Test
    void lateReturnsPayAFinePerDay() {
        LoanResponse r = issue(reserve("m1", java));
        makeOverdue(r, 4);
        loginAs("lib", Role.LIBRARIAN);
        LoanResponse listed = library.allLoans(LoanStatus.ISSUED, null).get(0);
        assertTrue(listed.overdue());
        assertEquals(4, listed.daysOverdue());
        assertEquals(20, listed.fine()); // projected: 4 days at 5 per day
        LoanResponse back = library.giveBack(r.id(), "lib");
        assertEquals(20, back.fine());
        assertFalse(back.overdue());
    }

    @Test
    void librarianFiltersLoansByStatusAndText() {
        reserve("m1", java);
        issue(reserve("m2", sapiens));
        loginAs("lib", Role.LIBRARIAN);
        assertEquals(2, library.allLoans(null, null).size());
        assertEquals(1, library.allLoans(LoanStatus.RESERVED, null).size());
        assertEquals(1, library.allLoans(null, "member 2").size());
        assertEquals(1, library.allLoans(null, "sapiens").size());
    }

    // ---------- books ----------

    @Test
    void librarianAddsBooksWithValidatedUniqueIsbn() {
        loginAs("lib", Role.LIBRARIAN);
        BookResponse created = library.createBook(new BookBody(" Dune Messiah ", "Frank Herbert", "978-0-441-17269-5", "Fiction", " ", 1969, 2));
        assertEquals("9780441172695", created.isbn());
        assertEquals(2, created.availableCopies());
        FieldValidationException dup = assertThrows(FieldValidationException.class,
                () -> library.createBook(new BookBody("Copy", "x", "9780441172695", "Fiction", null, null, 1)));
        assertEquals("isbn", dup.getField());
        assertThrows(FieldValidationException.class, () -> library.createBook(new BookBody("Bad", "x", "12345", "Fiction", null, null, 1)));
    }

    @Test
    void copiesCannotDropBelowTheOnesInUse() {
        reserve("m1", java);
        issue(reserve("m2", java));
        loginAs("lib", Role.LIBRARIAN);
        FieldValidationException ex = assertThrows(FieldValidationException.class,
                () -> library.updateBook(java.getId(), new BookBody("Effective Java", "Joshua Bloch", "9780134685991", "Technology", null, 2018, 1)));
        assertEquals("totalCopies", ex.getField());
        BookResponse updated = library.updateBook(java.getId(), new BookBody("Effective Java 3rd", "Joshua Bloch", "9780134685991", "Technology", null, 2018, 4));
        assertEquals(2, updated.availableCopies());
    }

    @Test
    void dashboardCountsHoldingsOverdueAndFines() {
        LoanResponse overdue = issue(reserve("m1", java));
        makeOverdue(overdue, 2);
        reserve("m2", sapiens);
        LoanResponse late = issue(reserve("m3", atomic));
        makeOverdue(late, 3);
        giveBack(late); // fine 15
        loginAs("lib", Role.LIBRARIAN);
        LibraryDashboard d = library.dashboard();
        assertEquals(5, d.titles());
        assertEquals(12, d.copies());
        assertEquals(1, d.reserved());
        assertEquals(1, d.issued());
        assertEquals(10, d.available()); // 12 copies, 2 held
        assertEquals(1, d.overdue());
        assertEquals(15, d.finesCollected());
        assertEquals("Effective Java", d.overdueLoans().get(0).bookTitle());
        assertEquals(4, d.byCategory().size());
    }

    @Test
    void rolesAreEnforced() {
        loginAs("m1", Role.MEMBER);
        assertThrows(AccessDeniedException.class, () -> library.dashboard());
        assertThrows(AccessDeniedException.class, () -> library.allLoans(null, null));
        assertThrows(AccessDeniedException.class, () -> library.issue(1L, "m1"));
        loginAs("lib", Role.LIBRARIAN);
        assertThrows(AccessDeniedException.class, () -> library.reserve("lib", java.getId()));
        assertThrows(AccessDeniedException.class, () -> library.myLoans("lib", null));
    }

    // ---------- concurrency ----------

    @Test
    void lastCopyGoesToExactlyOneMemberWhenManyReserveAtOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Boolean>> tasks = IntStream.rangeClosed(1, 6).<Callable<Boolean>>mapToObj(i -> reserveTask("m" + i, single)).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, loans.countByBookAndStatusIn(books.findById(single.getId()).orElseThrow(), LoanStatus.HOLDING));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void sameMemberDoubleClickingReservesOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 4).<Callable<Boolean>>mapToObj(i -> reserveTask("m1", sapiens)).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, loans.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void memberLimitHoldsWhenReservingFiveBooksAtOnce() throws Exception {
        List<Book> five = List.of(java, sapiens, atomic, dune, single);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = five.stream().<Callable<Boolean>>map(b -> reserveTask("m1", b)).toList();
            assertEquals(3, winners(pool.invokeAll(tasks)));
            assertEquals(3, loans.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void issuingTheSameReservationFromTwoDesksIssuesItOnce() throws Exception {
        LoanResponse r = reserve("m1", java);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 2).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("lib", Role.LIBRARIAN);
                try {
                    library.issue(r.id(), "lib");
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Boolean> reserveTask(String member, Book book) {
        return () -> {
            loginAs(member, Role.MEMBER);
            try {
                library.reserve(member, book.getId());
                return true;
            } catch (ConflictException | BusinessRuleException ex) {
                return false;
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private long winners(List<Future<Boolean>> results) throws Exception {
        long count = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                count++;
            }
        }
        return count;
    }
}
