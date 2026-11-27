package com.hackathon.library.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.library.dto.BookBody;
import com.hackathon.library.dto.BookResponse;
import com.hackathon.library.dto.CategoryCount;
import com.hackathon.library.dto.LibraryDashboard;
import com.hackathon.library.dto.LoanResponse;
import com.hackathon.library.entity.Book;
import com.hackathon.library.entity.Loan;
import com.hackathon.library.entity.LoanStatus;
import com.hackathon.library.entity.User;
import com.hackathon.library.exception.BusinessRuleException;
import com.hackathon.library.exception.ConflictException;
import com.hackathon.library.exception.FieldValidationException;
import com.hackathon.library.exception.ForbiddenActionException;
import com.hackathon.library.exception.InvalidStatusException;
import com.hackathon.library.exception.ResourceNotFoundException;
import com.hackathon.library.repo.BookRepository;
import com.hackathon.library.repo.LoanRepository;
import com.hackathon.library.repo.UserRepository;

/**
 * Books, reservations, issuing and returns. Every change to a book's copies first locks that book's row (under
 * READ_COMMITTED), so the last copy can only be reserved once; a member's own limits are counted under a lock on the member.
 */
@Service
public class LibraryService {

    private final BookRepository books;
    private final LoanRepository loans;
    private final UserRepository users;
    private final int loanDays;
    private final int finePerDay;
    private final int maxActiveLoans;

    public LibraryService(BookRepository books, LoanRepository loans, UserRepository users,
                          @Value("${app.library.loan-days:14}") int loanDays,
                          @Value("${app.library.fine-per-day:5}") int finePerDay,
                          @Value("${app.library.max-active-loans:3}") int maxActiveLoans) {
        this.books = books;
        this.loans = loans;
        this.users = users;
        this.loanDays = loanDays;
        this.finePerDay = finePerDay;
        this.maxActiveLoans = maxActiveLoans;
    }

    // ---------- catalogue (any signed-in user) ----------

    /** Search by title, author or ISBN; filter by category and by "only books I can reserve now". */
    @Transactional(readOnly = true)
    public List<BookResponse> search(String username, String search, String category, boolean availableOnly) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        Map<Long, Long> held = loans.findByStatusIn(LoanStatus.HOLDING).stream()
                .collect(Collectors.groupingBy(l -> l.getBook().getId(), Collectors.counting()));
        Map<Long, Loan> mine = loans.findByStatusIn(LoanStatus.HOLDING).stream()
                .filter(l -> l.getMember().getUsername().equals(username))
                .collect(Collectors.toMap(l -> l.getBook().getId(), l -> l));
        return books.findAllByOrderByTitleAsc().stream()
                .filter(b -> category == null || category.isBlank() || b.getCategory().equalsIgnoreCase(category))
                .filter(b -> term.isEmpty() || b.getTitle().toLowerCase(Locale.ROOT).contains(term)
                        || b.getAuthor().toLowerCase(Locale.ROOT).contains(term) || b.getIsbn().contains(term.replaceAll("[\\s-]", "")))
                .map(b -> toResponse(b, held, mine))
                .filter(b -> !availableOnly || b.availableCopies() > 0)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return books.findAll().stream().map(Book::getCategory).distinct().sorted().collect(Collectors.toList());
    }

    // ---------- member ----------

    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanResponse reserve(String username, Long bookId) {
        Book book = books.findByIdForUpdate(bookId).orElseThrow(() -> new FieldValidationException("bookId", "That book does not exist"));
        User member = users.findByUsernameForUpdate(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        if (loans.existsByBookAndMemberAndStatusIn(book, member, LoanStatus.HOLDING)) {
            throw new ConflictException("You already have '" + book.getTitle() + "' reserved or on loan");
        }
        long held = loans.countByBookAndStatusIn(book, LoanStatus.HOLDING);
        if (held >= book.getTotalCopies()) {
            throw new ConflictException("'" + book.getTitle() + "' is not available right now. All " + book.getTotalCopies() + " copies are reserved or on loan.");
        }
        LocalDate today = LocalDate.now();
        boolean hasOverdue = loans.findByMemberOrderByReservedAtDesc(member).stream()
                .anyMatch(l -> l.getStatus() == LoanStatus.ISSUED && l.getDueDate().isBefore(today));
        if (hasOverdue) {
            throw new BusinessRuleException("You have an overdue book. Please return it before reserving another.");
        }
        if (loans.countByMemberAndStatusIn(member, LoanStatus.HOLDING) >= maxActiveLoans) {
            throw new BusinessRuleException("You can hold at most " + maxActiveLoans + " books (reserved or on loan) at a time");
        }
        return toResponse(loans.save(new Loan(book, member)));
    }

    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(readOnly = true)
    public List<LoanResponse> myLoans(String username, LoanStatus status) {
        User member = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return loans.findByMemberOrderByReservedAtDesc(member).stream()
                .filter(l -> status == null || l.getStatus() == status).map(this::toResponse).collect(Collectors.toList());
    }

    /** A member cancels their own reservation (before the book is handed over). */
    @PreAuthorize("hasRole('MEMBER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanResponse cancel(String username, Long loanId) {
        Loan loan = lockBookThenLoad(loanId);
        if (!loan.getMember().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only cancel your own reservation");
        }
        if (loan.getStatus() != LoanStatus.RESERVED) {
            throw new InvalidStatusException("Only a reservation can be cancelled; this one is " + loan.getStatus().name().toLowerCase(Locale.ROOT));
        }
        loan.cancel();
        return toResponse(loan);
    }

    // ---------- librarian ----------

    /** Reservations and loans, newest first; filter by status and by member or book text. */
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Transactional(readOnly = true)
    public List<LoanResponse> allLoans(LoanStatus status, String search) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return loans.findAllByOrderByReservedAtDesc().stream()
                .filter(l -> status == null || l.getStatus() == status)
                .filter(l -> term.isEmpty() || l.getBook().getTitle().toLowerCase(Locale.ROOT).contains(term)
                        || l.getMember().getFullname().toLowerCase(Locale.ROOT).contains(term)
                        || l.getMember().getUsername().contains(term))
                .map(this::toResponse).collect(Collectors.toList());
    }

    /** Hands the reserved book to the member and sets the due date. */
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanResponse issue(Long loanId, String librarian) {
        Loan loan = lockBookThenLoad(loanId);
        if (loan.getStatus() != LoanStatus.RESERVED) {
            throw new InvalidStatusException("Only a reserved book can be issued; this one is " + loan.getStatus().name().toLowerCase(Locale.ROOT));
        }
        loan.issue(librarian, LocalDate.now().plusDays(loanDays));
        return toResponse(loan);
    }

    /** Takes the book back; the copy becomes available again and any late fine is recorded. */
    @PreAuthorize("hasRole('LIBRARIAN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanResponse giveBack(Long loanId, String librarian) {
        Loan loan = lockBookThenLoad(loanId);
        if (loan.getStatus() != LoanStatus.ISSUED) {
            throw new InvalidStatusException(loan.getStatus() == LoanStatus.RETURNED ? "This book has already been returned"
                    : "Only an issued book can be returned; this one is " + loan.getStatus().name().toLowerCase(Locale.ROOT));
        }
        loan.giveBack(librarian, fineFor(loan));
        return toResponse(loan);
    }

    @PreAuthorize("hasRole('LIBRARIAN')")
    @Transactional
    public BookResponse createBook(BookBody body) {
        String isbn = normaliseIsbn(body.isbn());
        if (books.existsByIsbn(isbn)) {
            throw duplicate(isbn);
        }
        try {
            Book saved = books.saveAndFlush(new Book(body.title().trim(), body.author().trim(), isbn, body.category().trim(),
                    blankToNull(body.description()), body.publishedYear(), body.totalCopies()));
            return BookResponse.from(saved, saved.getTotalCopies(), null, null);
        } catch (DataIntegrityViolationException ex) { // created at the same moment
            throw duplicate(isbn);
        }
    }

    @PreAuthorize("hasRole('LIBRARIAN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookResponse updateBook(Long id, BookBody body) {
        Book book = books.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + id));
        String isbn = normaliseIsbn(body.isbn());
        if (!book.getIsbn().equals(isbn) && books.existsByIsbn(isbn)) {
            throw duplicate(isbn);
        }
        long held = loans.countByBookAndStatusIn(book, LoanStatus.HOLDING);
        if (body.totalCopies() < held) {
            throw new FieldValidationException("totalCopies", held + " copies are reserved or on loan, so there cannot be fewer than " + held);
        }
        book.update(body.title().trim(), body.author().trim(), isbn, body.category().trim(), blankToNull(body.description()),
                body.publishedYear(), body.totalCopies());
        return BookResponse.from(book, (int) (book.getTotalCopies() - held), null, null);
    }

    @PreAuthorize("hasRole('LIBRARIAN')")
    @Transactional(readOnly = true)
    public LibraryDashboard dashboard() {
        List<Book> all = books.findAllByOrderByTitleAsc();
        List<Loan> holding = loans.findByStatusIn(LoanStatus.HOLDING);
        Map<Long, Long> held = holding.stream().collect(Collectors.groupingBy(l -> l.getBook().getId(), Collectors.counting()));
        LocalDate today = LocalDate.now();
        List<LoanResponse> overdue = holding.stream()
                .filter(l -> l.getStatus() == LoanStatus.ISSUED && l.getDueDate().isBefore(today))
                .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate())).map(this::toResponse).collect(Collectors.toList());
        int copies = all.stream().mapToInt(Book::getTotalCopies).sum();
        long heldTotal = held.values().stream().mapToLong(Long::longValue).sum();
        Map<String, List<Book>> byCategory = all.stream().collect(Collectors.groupingBy(Book::getCategory));
        List<CategoryCount> categories = byCategory.keySet().stream().sorted().map(c -> {
            List<Book> list = byCategory.get(c);
            int total = list.stream().mapToInt(Book::getTotalCopies).sum();
            long inUse = list.stream().mapToLong(b -> held.getOrDefault(b.getId(), 0L)).sum();
            return new CategoryCount(c, list.size(), total, (int) (total - inUse));
        }).collect(Collectors.toList());
        long reserved = holding.stream().filter(l -> l.getStatus() == LoanStatus.RESERVED).count();
        int fines = loans.findAllByOrderByReservedAtDesc().stream().filter(l -> l.getFine() != null).mapToInt(Loan::getFine).sum();
        return new LibraryDashboard(all.size(), copies, (int) (copies - heldTotal), reserved, holding.size() - reserved,
                overdue.size(), fines, categories, overdue);
    }

    // ---------- helpers ----------

    /** Locks the loan's book row first, then loads the loan, so the loan is never read stale. */
    private Loan lockBookThenLoad(Long loanId) {
        Long bookId = loans.bookIdOf(loanId).orElseThrow(() -> new ResourceNotFoundException("Loan not found with id " + loanId));
        books.findByIdForUpdate(bookId);
        return loans.findById(loanId).orElseThrow();
    }

    private BookResponse toResponse(Book b, Map<Long, Long> held, Map<Long, Loan> mine) {
        Loan own = mine.get(b.getId());
        return BookResponse.from(b, (int) Math.max(0, b.getTotalCopies() - held.getOrDefault(b.getId(), 0L)),
                own == null ? null : own.getStatus().name(), own == null ? null : own.getId());
    }

    private LoanResponse toResponse(Loan l) {
        long late = daysOverdue(l);
        return LoanResponse.from(l, late, l.getStatus() == LoanStatus.RETURNED ? (l.getFine() == null ? 0 : l.getFine()) : (int) (late * finePerDay));
    }

    /** Whole days past the due date for a book that is still out (0 otherwise). */
    private long daysOverdue(Loan l) {
        if (l.getStatus() != LoanStatus.ISSUED || l.getDueDate() == null) {
            return 0;
        }
        return Math.max(0, ChronoUnit.DAYS.between(l.getDueDate(), LocalDate.now()));
    }

    private int fineFor(Loan l) {
        return (int) (daysOverdue(l) * finePerDay);
    }

    /** "978-0-13-468599-1" -> "9780134685991"; 10 digits (last may be X) or 13 digits. */
    static String normaliseIsbn(String raw) {
        String isbn = raw.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!isbn.matches("\\d{9}[\\dX]|\\d{13}")) {
            throw new FieldValidationException("isbn", "Enter a valid 10 or 13 digit ISBN");
        }
        return isbn;
    }

    private FieldValidationException duplicate(String isbn) {
        return new FieldValidationException("isbn", "A book with ISBN " + isbn + " already exists");
    }

    private String blankToNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }
}
