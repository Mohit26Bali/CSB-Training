package com.hackathon.library.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.library.entity.Book;

import jakarta.persistence.LockModeType;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    List<Book> findAllByOrderByTitleAsc();

    /** Row lock: reserving, issuing, returning and changing the copies of one book happen one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);
}
