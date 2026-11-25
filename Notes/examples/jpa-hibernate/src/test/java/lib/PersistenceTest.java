package lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lib.model.Author;
import lib.model.AuditLog;
import lib.model.Book;
import lib.model.Tag;
import lib.repo.AuditLogRepository;
import lib.repo.AuthorRepository;
import lib.repo.AuthorRepository.AuthorSummary;
import lib.repo.BookRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * @DataJpaTest starts only the JPA part of the application with an in-memory H2 database, and wraps EACH test in a
 * transaction that is rolled back at the end. Because the whole test is one transaction (one persistence context),
 * the tests call flush() and clear() to force real SQL.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=lib.SqlRecorder")
class PersistenceTest {

    @Autowired EntityManager em;
    @Autowired AuthorRepository authors;
    @Autowired BookRepository books;
    @Autowired AuditLogRepository audit;

    Long ashaId;
    Long firstBookId;

    /** Three authors with three books each, stored and then removed from memory, so tests read from the database. */
    @BeforeEach
    void seed() {
        Tag java = em.merge(new Tag("java"));
        Tag sql = em.merge(new Tag("sql"));
        int n = 0;
        for (String name : List.of("Asha", "Ben", "Chitra")) {
            Author author = new Author(name);
            for (int i = 1; i <= 3; i++) {
                Book b = new Book(name + " book " + i, 100 * i + (++n));
                b.getTags().add(i % 2 == 0 ? java : sql);
                author.addBook(b);
            }
            authors.save(author);
            if (firstBookId == null) firstBookId = author.getBooks().get(0).getId();   // identity ids are assigned at persist time
        }
        em.flush();
        em.clear();                                            // forget everything: the next read hits the database
        ashaId = authors.findByName("Asha").orElseThrow().getId();
        em.clear();
        SqlRecorder.clear();
    }

    // ---------- the persistence context ----------

    @Test
    void firstLevelCacheReturnsTheSameObjectWithoutAnotherQuery() {
        Book first = books.findById(firstBookId).orElseThrow();
        Book second = books.findById(firstBookId).orElseThrow();
        assertThat(second).isSameAs(first);                    // the very same object
        assertThat(SqlRecorder.selects()).isEqualTo(1);        // one SELECT, not two
    }

    @Test
    void dirtyCheckingWritesChangesWithoutAnySaveCall() {
        Book book = books.findById(firstBookId).orElseThrow();
        book.setTitle("A new title");                          // no save(), no persist()
        SqlRecorder.clear();

        em.flush();                                            // happens automatically at commit

        assertThat(SqlRecorder.count("update book")).isEqualTo(1);
        assertThat(book.getVersion()).isEqualTo(1);            // @Version went from 0 to 1
    }

    @Test
    void entityStatesNewManagedDetached() {
        Author fresh = new Author("Dev");
        assertThat(em.contains(fresh)).isFalse();              // new (transient)
        em.persist(fresh);
        assertThat(em.contains(fresh)).isTrue();               // managed
        em.flush();
        em.detach(fresh);
        assertThat(em.contains(fresh)).isFalse();              // detached: changes are no longer tracked

        fresh.setName("Dev Renamed");
        SqlRecorder.clear();
        em.flush();
        assertThat(SqlRecorder.count("update author")).isZero();   // nothing was written

        Author managedCopy = em.merge(fresh);                  // merge copies the state onto a NEW managed instance
        assertThat(managedCopy).isNotSameAs(fresh);
        assertThat(em.contains(managedCopy)).isTrue();
        assertThat(managedCopy.getName()).isEqualTo("Dev Renamed");
    }

    @Test
    void getReferenceGivesAProxyWithoutQuerying() {
        Author proxy = em.getReference(Author.class, ashaId);
        assertThat(SqlRecorder.selects()).isZero();            // no SELECT yet
        assertThat(Hibernate.isInitialized(proxy)).isFalse();
        assertThat(proxy.getName()).isEqualTo("Asha");         // first real access loads it
        assertThat(SqlRecorder.selects()).isEqualTo(1);
    }

    // ---------- lazy loading and the N+1 problem ----------

    @Test
    void theNPlusOneProblem() {
        List<Author> all = authors.findAll();                                  // 1 query for the authors
        int bookCount = 0;
        for (Author a : all) bookCount += a.getBooks().size();                 // + 1 query PER author for its books
        assertThat(bookCount).isEqualTo(9);
        assertThat(SqlRecorder.selects()).isEqualTo(1 + 3);                    // 4 queries for 3 authors: 1 + N
    }

    @Test
    void joinFetchLoadsEverythingInOneQuery() {
        List<Author> all = authors.findAllWithBooks();
        int bookCount = all.stream().mapToInt(a -> a.getBooks().size()).sum();
        assertThat(bookCount).isEqualTo(9);
        assertThat(SqlRecorder.selects()).isEqualTo(1);
    }

    @Test
    void anEntityGraphDoesTheSame() {
        List<Author> all = authors.findAllWithBooksGraph();
        assertThat(all.stream().mapToInt(a -> a.getBooks().size()).sum()).isEqualTo(9);
        assertThat(SqlRecorder.selects()).isEqualTo(1);
    }

    @Test
    void aProjectionReadsOnlyWhatItNeeds() {
        List<AuthorSummary> summaries = authors.summaries();
        assertThat(summaries).extracting(AuthorSummary::name).containsExactly("Asha", "Ben", "Chitra");
        assertThat(summaries).extracting(AuthorSummary::bookCount).containsExactly(3L, 3L, 3L);
        assertThat(SqlRecorder.selects()).isEqualTo(1);
        assertThat(SqlRecorder.all().get(0).toLowerCase()).doesNotContain("pages");     // book columns were never selected
    }

    @Test
    void anInterfaceProjectionSelectsOnlyTheNamedColumns() {
        List<BookRepository.TitleAndPages> found = books.findByAuthorName("Ben");
        assertThat(found).hasSize(3);
        assertThat(found.get(0).getTitle()).startsWith("Ben book");
        String sql = SqlRecorder.all().get(0).toLowerCase();
        assertThat(sql).contains("title").doesNotContain("status");
    }

    @Test
    void collectionFetchTogetherWithPagingIsPagedInTheDatabaseInHibernate7() {
        // Hibernate 7 pages the ROOT rows in a subquery and then joins the books, so both the limit and the fetch are correct.
        // (Hibernate 6 and earlier fetched every row and cut the list in memory, logging warning HHH90003004.)
        List<Author> firstTwo = em.createQuery("select distinct a from Author a join fetch a.books order by a.name", Author.class)
            .setMaxResults(2).getResultList();
        assertThat(firstTwo).extracting(Author::getName).containsExactly("Asha", "Ben");
        assertThat(firstTwo.stream().mapToInt(a -> a.getBooks().size()).sum()).isEqualTo(6);    // complete collections, not truncated
        assertThat(SqlRecorder.selects()).isEqualTo(1);
        assertThat(SqlRecorder.all().get(0).toLowerCase()).contains("fetch first");
    }

    @Test
    void pagingWithoutAFetchedCollectionUsesALimit() {
        Page<Author> page = authors.page(PageRequest.of(0, 2));
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(SqlRecorder.all().stream().anyMatch(s -> s.toLowerCase().contains("fetch first") || s.toLowerCase().contains(" limit "))).isTrue();
        assertThat(SqlRecorder.count("select count")).isEqualTo(1);                  // the extra count query for Page
    }

    // ---------- relationships ----------

    @Test
    void orphanRemovalDeletesABookRemovedFromItsAuthor() {
        Author asha = authors.findById(ashaId).orElseThrow();
        Book first = asha.getBooks().get(0);
        asha.removeBook(first);
        em.flush();
        em.clear();
        assertThat(books.findById(first.getId())).isEmpty();
        assertThat(books.count()).isEqualTo(8);
    }

    @Test
    void cascadeRemoveDeletesTheChildren() {
        authors.deleteById(ashaId);
        em.flush();
        assertThat(books.count()).isEqualTo(6);                 // Asha's 3 books went with her
    }

    @Test
    void theOwningSideDecidesWhatIsWrittenToTheDatabase() {
        Author asha = authors.findById(ashaId).orElseThrow();
        Book stray = new Book("Not linked", 50);
        asha.getBooks().add(stray);                              // only the INVERSE side was set; stray.author is still null
        assertThatThrownBy(() -> { em.persist(stray); em.flush(); })    // the owning side is null and the column is NOT NULL
            .isInstanceOf(Exception.class);
    }

    @Test
    void manyToManyRowsLiveInAJoinTable() {
        Number rows = (Number) em.createNativeQuery("select count(*) from book_tag").getSingleResult();
        assertThat(rows.intValue()).isEqualTo(9);                // one row per (book, tag) link
        Book book = books.findById(firstBookId).orElseThrow();
        assertThat(book.getTags()).extracting(Tag::getName).containsExactly("sql");
    }

    // ---------- queries ----------

    @Test
    void derivedQueriesAreBuiltFromTheMethodName() {
        assertThat(authors.findByName("Ben")).isPresent();
        assertThat(authors.existsByName("Nobody")).isFalse();
        assertThat(authors.findByNameStartingWithIgnoreCaseOrderByNameAsc("c")).extracting(Author::getName).containsExactly("Chitra");
        assertThat(books.findByPagesGreaterThanOrderByPagesDesc(600)).extracting(Book::getPages).isSortedAccordingTo((a, b) -> b - a);
        assertThat(authors.search("EN")).extracting(Author::getName).containsExactly("Ben");
    }

    @Test
    void aBulkUpdateBypassesThePersistenceContext() {
        Book book = books.findAll().stream().filter(b -> b.getPages() < 150).findFirst().orElseThrow();
        assertThat(book.getStatus()).isEqualTo(Book.Status.DRAFT);

        int changed = books.markShortBooks(Book.Status.OUT_OF_PRINT, 400);        // runs straight in the database
        assertThat(changed).isGreaterThan(0);
        assertThat(book.getStatus()).isEqualTo(Book.Status.DRAFT);                // the object in memory is STALE

        em.clear();
        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(Book.Status.OUT_OF_PRINT);
    }

    @Test
    void clearAutomaticallyKeepsMemoryInStepWithTheDatabase() {
        Book book = books.findAll().stream().filter(b -> b.getPages() < 150).findFirst().orElseThrow();
        books.markShortBooksSafely(Book.Status.OUT_OF_PRINT, 400);
        assertThat(books.findById(book.getId()).orElseThrow().getStatus()).isEqualTo(Book.Status.OUT_OF_PRINT);
    }

    @Test
    void pessimisticLockUsesSelectForUpdate() {
        books.findForUpdate(firstBookId);
        assertThat(SqlRecorder.all().get(0).toLowerCase()).contains("for update");
    }

    // ---------- batching and ids ----------

    @Test
    void sequenceIdsAllowJdbcBatchInserts() {
        SqlRecorder.clear();
        audit.saveAll(java.util.stream.IntStream.range(0, 100).mapToObj(i -> new AuditLog("event " + i)).toList());
        em.flush();
        assertThat(SqlRecorder.count("insert into audit_log")).isEqualTo(1);       // ONE prepared statement, executed in batches of 20
    }

    @Test
    void identityIdsForceOneInsertPerRow() {
        SqlRecorder.clear();
        for (int i = 0; i < 100; i++) em.persist(new Tag("tag-" + i));
        em.flush();
        assertThat(SqlRecorder.count("insert into tag")).isEqualTo(100);           // IDENTITY needs each id immediately: no batching
    }

    // ---------- equals and hashCode ----------

    @Test
    void entityEqualityIsStableAcrossTheLifecycle() {
        Author author = new Author("Eq");
        Book fresh = new Book("T", 10);
        author.addBook(fresh);
        Set<Book> set = new HashSet<>();
        set.add(fresh);
        em.persist(author);                                    // the book gets an id (cascade)
        em.flush();
        assertThat(set).contains(fresh);                       // still found: hashCode did not depend on the id
        assertThat(new Book("T", 10)).isNotEqualTo(new Book("T", 10));   // two unsaved books are different objects
    }
}
