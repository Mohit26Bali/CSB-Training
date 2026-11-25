package com.csb.cinescope.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.csb.cinescope.entity.Quote;

/** Queries on {@link Quote}. Written to by {@code config/QuoteSeeder}; read by the sign-in screens and the quiz. */
public interface QuoteRepository extends JpaRepository<Quote, Long> {

    List<Quote> findByCategory(String category);
}
