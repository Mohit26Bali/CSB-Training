package lib.repo;

import java.util.List;
import java.util.Optional;
import lib.model.Author;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // derived query: Spring Data builds the JPQL from the method name
    Optional<Author> findByName(String name);

    List<Author> findByNameStartingWithIgnoreCaseOrderByNameAsc(String prefix);

    boolean existsByName(String name);

    // JPQL with a named parameter
    @Query("select a from Author a where lower(a.name) like lower(concat('%', :text, '%')) order by a.name")
    List<Author> search(@Param("text") String text);

    // fixes the N+1 problem: one query with a JOIN FETCH
    @Query("select distinct a from Author a join fetch a.books order by a.name")
    List<Author> findAllWithBooks();

    // the same fix, declared with an entity graph
    @EntityGraph(attributePaths = "books")
    @Query("select a from Author a order by a.name")
    List<Author> findAllWithBooksGraph();

    // paging an Author list is safe because no collection is fetched
    @Query("select a from Author a")
    Page<Author> page(Pageable pageable);

    // a projection straight into a record: only the columns that are needed
    record AuthorSummary(String name, long bookCount) { }

    @Query("select new lib.repo.AuthorRepository$AuthorSummary(a.name, count(b)) from Author a left join a.books b group by a.id, a.name order by a.name")
    List<AuthorSummary> summaries();
}
