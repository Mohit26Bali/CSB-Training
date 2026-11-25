package lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import lib.model.Author;
import lib.model.Book;
import lib.repo.AuditLogRepository;
import lib.repo.AuthorRepository;
import lib.repo.BookRepository;
import lib.service.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * No @Transactional on the TEST here, so every service call commits or rolls back for real,
 * and each test can see the result in a fresh transaction.
 */
@SpringBootTest
class TransactionTest {

    @Autowired CatalogService service;
    @Autowired AuthorRepository authors;
    @Autowired BookRepository books;
    @Autowired AuditLogRepository audit;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void clean() {
        books.deleteAll();
        authors.deleteAll();
        audit.deleteAll();
    }

    @Test
    void aRuntimeExceptionRollsBack() {
        assertThatThrownBy(() -> service.addAuthorThenFailWithRuntimeException("Rolled back"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(authors.existsByName("Rolled back")).isFalse();
    }

    @Test
    void aCheckedExceptionCommitsByDefault() {
        assertThatThrownBy(() -> service.addAuthorThenFailWithCheckedException("Committed anyway"))
            .isInstanceOf(IOException.class);
        assertThat(authors.existsByName("Committed anyway")).isTrue();            // surprising, and a classic interview question
    }

    @Test
    void rollbackForMakesACheckedExceptionRollBack() {
        assertThatThrownBy(() -> service.addAuthorThenFailWithCheckedExceptionAndRollbackFor("Rolled back too"))
            .isInstanceOf(IOException.class);
        assertThat(authors.existsByName("Rolled back too")).isFalse();
    }

    @Test
    void requiresNewKeepsTheAuditRowWhenTheOuterTransactionRollsBack() {
        assertThatThrownBy(() -> service.addAuthorWithAuditThenFail("Audited")).isInstanceOf(IllegalStateException.class);
        assertThat(authors.existsByName("Audited")).isFalse();                    // the main work was undone
        assertThat(audit.findAll()).extracting(a -> a.getMessage()).containsExactly("tried to add Audited");   // the audit survived
    }

    @Test
    void selfInvocationIgnoresRequiresNew() {
        assertThatThrownBy(() -> service.addAuthorWithSelfCalledAuditThenFail("Lost audit")).isInstanceOf(IllegalStateException.class);
        assertThat(audit.findAll()).isEmpty();                                    // the "separate" transaction was never created
    }

    @Test
    void lazyCollectionsCannotBeReadAfterTheTransactionHasEnded() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(status -> {
            Author author = new Author("Lazy");
            author.addBook(new Book("B", 10));
            authors.save(author);
        });
        Author loaded = authors.findByName("Lazy").orElseThrow();            // the repository call ends its transaction here
        assertThatThrownBy(() -> loaded.getBooks().size()).isInstanceOf(org.hibernate.LazyInitializationException.class);

        int size = tx.execute(status -> authors.findByName("Lazy").orElseThrow().getBooks().size());   // inside a transaction it works
        assertThat(size).isEqualTo(1);
    }

    @Test
    void optimisticLockingStopsALostUpdate() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Long bookId = tx.execute(status -> {
            Author author = new Author("Locked");
            author.addBook(new Book("Original", 100));
            return authors.save(author).getBooks().get(0).getId();
        });

        // two users open the same book at the same time (both see version 0)
        Book userA = tx.execute(status -> books.findById(bookId).orElseThrow());
        Book userB = tx.execute(status -> books.findById(bookId).orElseThrow());

        userA.setTitle("Changed by A");
        tx.executeWithoutResult(status -> books.save(userA));                     // A saves first: version becomes 1

        userB.setTitle("Changed by B");                                           // B still holds version 0
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> books.save(userB)))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);         // B is told to reload instead of overwriting A

        String stored = tx.execute(status -> books.findById(bookId).orElseThrow().getTitle());
        assertThat(stored).isEqualTo("Changed by A");
    }
}
