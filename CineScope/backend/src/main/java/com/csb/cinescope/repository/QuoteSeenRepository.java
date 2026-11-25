package com.csb.cinescope.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.csb.cinescope.entity.QuoteSeen;

/** Queries on {@link QuoteSeen}, used by {@code service/QuoteService}. */
public interface QuoteSeenRepository extends JpaRepository<QuoteSeen, Long> {

    /** Of these quotes, the ones this person has already been shown. */
    @Query("select s.quoteId from QuoteSeen s where s.userId = :userId and s.quoteId in :quoteIds")
    List<Long> seenAmong(@Param("userId") long userId, @Param("quoteIds") Collection<Long> quoteIds);

    /** The five quotes among these that this person was shown most recently (the newest id breaks ties within one second). */
    List<QuoteSeen> findTop5ByUserIdAndQuoteIdInOrderBySeenAtDescIdDesc(long userId, Collection<Long> quoteIds);

    /** Starts a new round: forgets that this person saw these quotes. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from QuoteSeen s where s.userId = :userId and s.quoteId in :quoteIds")
    void forget(@Param("userId") long userId, @Param("quoteIds") Collection<Long> quoteIds);
}
