package com.hackathon.library.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.library.entity.Book;
import com.hackathon.library.entity.Loan;
import com.hackathon.library.entity.LoanStatus;
import com.hackathon.library.entity.User;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    /** The book id only, so the loan is not loaded before the book row is locked. */
    @Query("select l.book.id from Loan l where l.id = :id")
    Optional<Long> bookIdOf(@Param("id") Long id);

    long countByBookAndStatusIn(Book book, Collection<LoanStatus> statuses);

    long countByMemberAndStatusIn(User member, Collection<LoanStatus> statuses);

    boolean existsByBookAndMemberAndStatusIn(Book book, User member, Collection<LoanStatus> statuses);

    @EntityGraph(attributePaths = { "book", "member" })
    List<Loan> findByMemberOrderByReservedAtDesc(User member);

    @EntityGraph(attributePaths = { "book", "member" })
    List<Loan> findAllByOrderByReservedAtDesc();

    @EntityGraph(attributePaths = { "book", "member" })
    List<Loan> findByStatusIn(Collection<LoanStatus> statuses);
}
