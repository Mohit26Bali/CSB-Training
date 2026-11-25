package lib.repo;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lib.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByStatus(Book.Status status);

    List<Book> findByPagesGreaterThanOrderByPagesDesc(int pages);

    // an interface projection: Spring creates a proxy holding only title and pages
    interface TitleAndPages {
        String getTitle();
        int getPages();
    }
    List<TitleAndPages> findByAuthorName(String authorName);

    // a bulk update runs directly in the database and bypasses the persistence context
    @Modifying
    @Query("update Book b set b.status = :status where b.pages < :pages")
    int markShortBooks(@Param("status") Book.Status status, @Param("pages") int pages);

    @Modifying(clearAutomatically = true, flushAutomatically = true)    // flush pending changes first, clear stale entities afterwards
    @Query("update Book b set b.status = :status where b.pages < :pages")
    int markShortBooksSafely(@Param("status") Book.Status status, @Param("pages") int pages);

    // pessimistic lock: SELECT ... FOR UPDATE
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findForUpdate(@Param("id") Long id);
}
