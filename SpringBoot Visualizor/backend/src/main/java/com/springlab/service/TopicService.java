package com.springlab.service;

import com.springlab.dto.challenge.TopicResponse;
import com.springlab.repository.TopicRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TopicService {

    private final TopicRepository topicRepository;

    public TopicService(TopicRepository topicRepository) {
        this.topicRepository = topicRepository;
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> list() {
        return topicRepository.findAllWithActiveQuestionCount();
    }
}
