package com.springlab.repository;

import com.springlab.entity.Difficulty;
import com.springlab.entity.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /** Ids of the questions an attempt may draw from. Only ACTIVE questions are ever eligible. */
    @Query("""
            select q.id from Question q
            where q.active = true
              and (:topicId is null or q.topic.id = :topicId)
              and (:difficulty is null or q.difficulty = :difficulty)
            """)
    List<Long> findActiveIds(@Param("topicId") Long topicId, @Param("difficulty") Difficulty difficulty);

    /** Loads questions with topic and options in one query (avoids N+1 when building views). */
    @Query("select distinct q from Question q join fetch q.topic left join fetch q.options where q.id in :ids")
    List<Question> findAllWithOptionsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            select q from Question q join fetch q.topic left join fetch q.createdBy left join fetch q.options
            where q.id = :id
            """)
    Optional<Question> findDetailById(@Param("id") Long id);

    /** Topic and creator are loaded through the entity graph, so listing a page does not issue one query per row. */
    @EntityGraph(attributePaths = {"topic", "createdBy"})
    @Query("""
            select q from Question q
            where (:topicId is null or q.topic.id = :topicId)
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:active is null or q.active = :active)
              and (:creatorId is null or q.createdBy.id = :creatorId)
              and (:text is null or lower(q.text) like lower(concat('%', :text, '%')))
            """)
    Page<Question> search(@Param("topicId") Long topicId, @Param("difficulty") Difficulty difficulty,
                          @Param("active") Boolean active, @Param("creatorId") Long creatorId,
                          @Param("text") String text, Pageable pageable);
}
