package com.springlab.service;

import com.springlab.dto.challenge.AttemptSummary;
import com.springlab.dto.progress.LeaderboardEntry;
import com.springlab.dto.progress.ProgressResponse;
import com.springlab.entity.AttemptStatus;
import com.springlab.entity.User;
import com.springlab.exception.InvalidRequestException;
import com.springlab.mapper.AttemptMapper;
import com.springlab.repository.AttemptRepository;
import com.springlab.repository.TopicRepository;
import com.springlab.repository.UserRepository;
import com.springlab.repository.projection.AttemptTotals;
import com.springlab.repository.projection.BreakdownRow;
import com.springlab.repository.projection.LeaderboardRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** All figures are aggregated from the stored attempts and answers; nothing is cached or precomputed. */
@Service
public class ProgressService {

    static final int MAX_LEADERBOARD_SIZE = 50;

    private final UserRepository userRepository;
    private final AttemptRepository attemptRepository;
    private final TopicRepository topicRepository;

    public ProgressService(UserRepository userRepository, AttemptRepository attemptRepository,
                           TopicRepository topicRepository) {
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.topicRepository = topicRepository;
    }

    @Transactional(readOnly = true)
    public ProgressResponse progressFor(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        AttemptTotals totals = attemptRepository.totalsFor(user.getId());
        List<AttemptSummary> recent = attemptRepository
                .findTop5ByUserIdAndStatusOrderBySubmittedAtDesc(user.getId(), AttemptStatus.SUBMITTED).stream()
                .map(AttemptMapper::toSummary).toList();
        return new ProgressResponse(
                totals.attempts(),
                totals.questions(),
                totals.correct(),
                percent(totals.correct(), totals.questions()),
                toBreakdown(attemptRepository.breakdownByTopic(user.getId())),
                toBreakdown(attemptRepository.breakdownByDifficulty(user.getId())),
                recent);
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntry> leaderboard(String topicSlug, int limit) {
        String slug = (topicSlug == null || topicSlug.isBlank()) ? null : topicSlug.trim();
        if (slug != null && topicRepository.findBySlug(slug).isEmpty()) {
            throw new InvalidRequestException("Unknown topic '" + slug + "'");
        }
        int size = Math.min(Math.max(limit, 1), MAX_LEADERBOARD_SIZE);
        List<LeaderboardRow> rows = attemptRepository.leaderboard(slug, PageRequest.of(0, size));

        List<LeaderboardEntry> entries = new ArrayList<>();
        int rank = 0;
        LeaderboardRow previous = null;
        for (int i = 0; i < rows.size(); i++) {
            LeaderboardRow row = rows.get(i);
            // Competition ranking: equal points AND equal questions attempted share the same rank.
            if (previous == null || row.points() != previous.points() || row.questions() != previous.questions()) {
                rank = i + 1;
            }
            entries.add(new LeaderboardEntry(rank, row.username(), row.points(), row.attempts(), row.questions(),
                    percent(row.points(), row.questions())));
            previous = row;
        }
        return entries;
    }

    private static List<ProgressResponse.Breakdown> toBreakdown(List<BreakdownRow> rows) {
        return rows.stream()
                .map(r -> new ProgressResponse.Breakdown(r.key(), r.label(), r.questions(), r.correct(),
                        percent(r.correct(), r.questions())))
                .toList();
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0 : Math.round(1000.0 * part / whole) / 10.0;
    }
}
