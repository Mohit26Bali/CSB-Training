package lib;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import lib.model.Author;
import lib.model.Book;
import lib.repo.AuthorRepository;
import lib.repo.BookRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** Prints the SQL behind each technique, so you can read what Hibernate really sends. Run: mvn -Dtest=SqlShowcaseTest test */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.session_factory.statement_inspector=lib.SqlRecorder")
class SqlShowcaseTest {

    @Autowired EntityManager em;
    @Autowired AuthorRepository authors;
    @Autowired BookRepository books;

    @BeforeEach
    void seed() {
        for (String name : List.of("Asha", "Ben")) {
            Author author = new Author(name);
            author.addBook(new Book(name + " one", 100));
            author.addBook(new Book(name + " two", 200));
            authors.save(author);
        }
        em.flush();
        em.clear();
        SqlRecorder.clear();
    }

    private void show(String title) {
        System.out.println("SQL>>> " + title);
        SqlRecorder.all().forEach(sql -> System.out.println("SQL>>>   " + sql));
        SqlRecorder.clear();
    }

    @Test
    void showTheSql() {
        authors.findAll().forEach(a -> a.getBooks().size());
        show("N+1: findAll() then a.getBooks() for each author");

        em.clear();
        authors.findAllWithBooks();
        show("JOIN FETCH");

        em.clear();
        authors.summaries();
        show("record projection with count");

        em.clear();
        books.findByAuthorName("Ben");
        show("interface projection (only title and pages)");

        em.clear();
        books.findForUpdate(books.findAll().get(0).getId());
        show("PESSIMISTIC_WRITE");

        em.clear();
        books.markShortBooks(Book.Status.OUT_OF_PRINT, 150);
        show("bulk update");

        assertThat(true).isTrue();
    }
}
