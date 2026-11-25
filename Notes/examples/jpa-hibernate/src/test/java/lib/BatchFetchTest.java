package lib;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import lib.model.Author;
import lib.model.Book;
import lib.repo.AuthorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** The same N+1 situation with batch fetching switched on: the books of several authors are loaded with one IN query. */
@DataJpaTest(properties = {
    "spring.jpa.properties.hibernate.session_factory.statement_inspector=lib.SqlRecorder",
    "spring.jpa.properties.hibernate.default_batch_fetch_size=10"
})
class BatchFetchTest {

    @Autowired EntityManager em;
    @Autowired AuthorRepository authors;

    @BeforeEach
    void seed() {
        for (String name : List.of("Asha", "Ben", "Chitra", "Dev", "Esha")) {
            Author author = new Author(name);
            for (int i = 1; i <= 2; i++) author.addBook(new Book(name + " book " + i, 100 * i));
            authors.save(author);
        }
        em.flush();
        em.clear();
        SqlRecorder.clear();
    }

    @Test
    void fiveAuthorsNeedTwoQueriesInsteadOfSix() {
        int books = 0;
        for (Author a : authors.findAll()) books += a.getBooks().size();
        assertThat(books).isEqualTo(10);
        assertThat(SqlRecorder.selects()).isEqualTo(2);        // 1 for the authors + 1 batched "where author_id in (?, ?, ?, ...)"
        assertThat(SqlRecorder.all().get(1).toLowerCase()).contains(" in (");
    }
}
