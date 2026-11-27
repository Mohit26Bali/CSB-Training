package com.springlab.repository;

import com.springlab.dto.challenge.TopicResponse;
import com.springlab.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    Optional<Topic> findBySlug(String slug);

    @Query("""
            select new com.springlab.dto.challenge.TopicResponse(t.id, t.name, t.slug, t.description,
                   (select count(q) from Question q where q.topic = t and q.active = true))
            from Topic t order by t.name
            """)
    List<TopicResponse> findAllWithActiveQuestionCount();
}
