package com.csb.cinescope.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.cinescope.dto.CommentRequest;
import com.csb.cinescope.dto.CommentView;
import com.csb.cinescope.dto.DiscussionSummary;
import com.csb.cinescope.dto.HelpfulResult;
import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.dto.ProcessingStep;
import com.csb.cinescope.entity.Comment;
import com.csb.cinescope.entity.CommentStatus;
import com.csb.cinescope.entity.CommentVote;
import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.Sentiment;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.exception.AppException;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.ConflictException;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.exception.TooManyAttemptsException;
import com.csb.cinescope.repository.CommentRepository;
import com.csb.cinescope.repository.CommentVoteRepository;
import com.csb.cinescope.repository.UserRepository;

/**
 * Reviews and discussion on titles: write a comment (optionally with a 1-10 rating), reply to one, edit or delete your own,
 * mark others' comments helpful, read the list, and see what the discussion adds up to. Also the moderation of held comments by
 * administrators. Called by {@code controller/CommentController} and {@code controller/AdminCommentController}.
 *
 * <p>Every new or edited text goes through {@link CommentProcessor} (cleaning, spam check, profanity filter, sentiment, decision);
 * what it decides is stored with the comment. Rules enforced here: a title must exist at TMDB (checked through
 * {@code CatalogService}, cached); a text is 2 to 1000 characters; a rating (1-10) only on a top-level comment and only once
 * per person per title; replies are one level deep and only to published comments; at most five new comments a minute per
 * person and no identical text twice within ten minutes; only the author may edit; the author or an administrator may delete
 * (a deleted comment takes its replies and helpful marks with it); you cannot mark your own comment helpful, and one mark per
 * person. A person sees every published comment plus their own held or rejected ones.
 */
@Service
public class CommentService {

    private static final Set<String> TYPES = Set.of("movie", "tv");
    private static final Set<String> SORTS = Set.of("helpful", "newest", "positive", "critical");
    private static final int MAX_PAGE_SIZE = 30;
    private static final int POSTS_PER_MINUTE = 5;
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(10);
    /** How many "votes" TMDB's own rating counts for when blended with the community's (see {@link DiscussionSummary}). */
    private static final double PRIOR_WEIGHT = 5;
    private static final int TOPIC_SAMPLE = 300;
    private static final int MAX_TOPICS = 8;

    private static final Set<String> STOP_WORDS = Set.of("this", "that", "with", "have", "from", "they", "were", "been", "what", "when", "will", "would",
            "their", "there", "about", "just", "like", "very", "really", "film", "movie", "show", "still", "much", "more", "most", "some", "than", "then",
            "them", "only", "also", "even", "into", "over", "such", "your", "after", "before", "because", "though", "which", "while", "where", "every",
            "could", "should", "these", "those", "being", "does", "doesn", "didn", "isn", "wasn", "don", "ever", "never", "always", "many", "make",
            "makes", "made", "watch", "watched", "seen", "feel", "felt", "thing", "things", "something", "anything", "everything", "again", "other",
            "first", "time", "twice", "once", "well", "give", "went", "come", "came", "know", "think", "thought", "found", "find", "here", "its");

    private final CommentRepository comments;
    private final CommentVoteRepository votes;
    private final UserRepository users;
    private final CommentProcessor processor;
    private final CatalogService catalog;

    public CommentService(CommentRepository comments, CommentVoteRepository votes, UserRepository users, CommentProcessor processor,
                          CatalogService catalog) {
        this.comments = comments;
        this.votes = votes;
        this.users = users;
        this.processor = processor;
        this.catalog = catalog;
    }

    // ---- reading ----

    /** One page of top-level comments of a title, each with its replies. {@code sort}: helpful, newest, positive or critical. */
    @Transactional(readOnly = true)
    public PageResult<CommentView> list(String type, long id, String sort, int page, int size, String username) {
        requireTitle(type, id);
        if (!SORTS.contains(sort)) {
            throw new BadRequestException("Unknown sort '" + sort + "'. Use one of " + new java.util.TreeSet<>(SORTS));
        }
        if (page < 1) {
            throw new BadRequestException("page must be 1 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        User me = user(username);
        Sort order = switch (sort) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt", "id");
            case "positive" -> Sort.by(Sort.Direction.DESC, "sentimentScore", "createdAt", "id");
            case "critical" -> Sort.by(Sort.Direction.ASC, "sentimentScore").and(Sort.by(Sort.Direction.DESC, "createdAt", "id"));
            default -> Sort.by(Sort.Direction.DESC, "helpfulCount", "createdAt", "id");
        };
        Page<Comment> found = comments.findTopLevel(type, id, me.getId(), PageRequest.of(page - 1, size, order));
        List<CommentView> items = withReplies(found.getContent(), me);
        return new PageResult<>(items, page, Math.max(1, found.getTotalPages()), found.getTotalElements());
    }

    /** What the published discussion of a title adds up to (ratings, blended score, mood, topics). */
    @Transactional(readOnly = true)
    public DiscussionSummary summary(String type, long id) {
        requireTitle(type, id);
        int commentCount = (int) comments.countByMediaTypeAndTmdbIdAndParentIsNullAndStatus(type, id, CommentStatus.PUBLISHED);
        int replyCount = (int) comments.countByMediaTypeAndTmdbIdAndParentIsNotNullAndStatus(type, id, CommentStatus.PUBLISHED);

        int[] counts = new int[10];
        double sum = 0;
        List<Integer> ratings = comments.publishedRatings(type, id);
        for (int rating : ratings) {
            counts[rating - 1]++;
            sum += rating;
        }
        Double average = ratings.isEmpty() ? null : round1(sum / ratings.size());
        List<Integer> histogram = new ArrayList<>();
        for (int n : counts) {
            histogram.add(n);
        }

        Double tmdb = tmdbRating(type, id);
        Double community = null;
        if (tmdb != null) {
            community = ratings.isEmpty() ? tmdb : round1((sum + PRIOR_WEIGHT * tmdb) / (ratings.size() + PRIOR_WEIGHT));
        } else if (average != null) {
            community = average;
        }

        int positive = 0;
        int neutral = 0;
        int negative = 0;
        for (CommentRepository.SentimentCount c : comments.sentimentCounts(type, id)) {
            switch (c.getSentiment()) {
                case POSITIVE -> positive = (int) c.getTotal();
                case NEGATIVE -> negative = (int) c.getTotal();
                default -> neutral = (int) c.getTotal();
            }
        }
        int all = positive + neutral + negative;
        int positivePercent = all == 0 ? 0 : (int) Math.round(100.0 * positive / all);
        int negativePercent = all == 0 ? 0 : (int) Math.round(100.0 * negative / all);
        int neutralPercent = all == 0 ? 0 : 100 - positivePercent - negativePercent;

        return new DiscussionSummary(commentCount, replyCount, ratings.size(), average, histogram, tmdb, community,
                new DiscussionSummary.SentimentShare(positive, neutral, negative, positivePercent, neutralPercent, negativePercent),
                topics(type, id));
    }

    // ---- writing ----

    @Transactional
    public CommentView post(String type, long id, CommentRequest request, String username) {
        requireTitle(type, id);
        User me = user(username);
        requireNotTooFast(me);
        catalog.detail(type, id); // 404 if the title does not exist at TMDB
        if (request.rating() != null && comments.existsByUserAndMediaTypeAndTmdbIdAndParentIsNullAndRatingIsNotNull(me, type, id)) {
            throw new ConflictException("You have already rated this title. Edit your review to change the rating");
        }
        CommentProcessor.Result result = processor.process(request.body());
        requireNotDuplicate(me, result.text());
        Comment saved = comments.save(new Comment(me, id, type, null, result.text(), request.rating(), result.sentiment(), result.sentimentScore(),
                result.status(), result.encodedSteps()));
        return view(saved, me, Set.of(), List.of());
    }

    @Transactional
    public CommentView reply(long parentId, CommentRequest request, String username) {
        User me = user(username);
        Comment parent = comments.load(parentId).orElseThrow(() -> new NotFoundException("Comment not found"));
        if (parent.getStatus() != CommentStatus.PUBLISHED) {
            throw new NotFoundException("Comment not found");
        }
        if (parent.getParent() != null) {
            throw new BadRequestException("You can reply to a comment, but not to a reply");
        }
        if (request.rating() != null) {
            throw new BadRequestException("Only a review can carry a rating, not a reply");
        }
        requireNotTooFast(me);
        CommentProcessor.Result result = processor.process(request.body());
        requireNotDuplicate(me, result.text());
        Comment saved = comments.save(new Comment(me, parent.getTmdbId(), parent.getMediaType(), parent, result.text(), null, result.sentiment(),
                result.sentimentScore(), result.status(), result.encodedSteps()));
        return view(saved, me, Set.of(), List.of());
    }

    /** Changes the text (and rating) of your own comment; the new text goes through the pipeline again. */
    @Transactional
    public CommentView edit(long id, CommentRequest request, String username) {
        User me = user(username);
        Comment comment = comments.load(id).orElseThrow(() -> new NotFoundException("Comment not found"));
        if (!comment.getUser().getId().equals(me.getId())) {
            throw new AccessDeniedException("You can only change your own comments");
        }
        boolean isReply = comment.getParent() != null;
        if (isReply && request.rating() != null) {
            throw new BadRequestException("Only a review can carry a rating, not a reply");
        }
        if (!isReply && request.rating() != null && comment.getRating() == null
                && comments.existsByUserAndMediaTypeAndTmdbIdAndParentIsNullAndRatingIsNotNull(me, comment.getMediaType(), comment.getTmdbId())) {
            throw new ConflictException("You have already rated this title in another review");
        }
        CommentProcessor.Result result = processor.process(request.body());
        comment.edit(result.text(), request.rating(), result.sentiment(), result.sentimentScore(), result.status(), result.encodedSteps());
        Comment saved = comments.save(comment);
        List<CommentView> replies = isReply ? List.of() : repliesOf(List.of(saved.getId()), me).getOrDefault(saved.getId(), List.of());
        return view(saved, me, votedAmong(me, List.of(saved.getId())), replies);
    }

    /** Deletes a comment with its replies and helpful marks. The author may; so may an administrator. */
    @Transactional
    public void delete(long id, String username) {
        User me = user(username);
        Comment comment = comments.load(id).orElseThrow(() -> new NotFoundException("Comment not found"));
        boolean owner = comment.getUser().getId().equals(me.getId());
        if (!owner && me.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("You can only delete your own comments");
        }
        List<Long> replyIds = comments.idsOfRepliesTo(List.of(id));
        List<Long> everything = new ArrayList<>(replyIds);
        everything.add(id);
        votes.deleteOnComments(everything);
        if (!replyIds.isEmpty()) {
            comments.deleteByIds(replyIds);
        }
        comments.deleteByIds(List.of(id));
    }

    /** Marks a comment helpful, or takes the mark back if it is already there. */
    @Transactional
    public HelpfulResult toggleHelpful(long id, String username) {
        User me = user(username);
        Comment comment = comments.load(id).orElseThrow(() -> new NotFoundException("Comment not found"));
        if (comment.getStatus() != CommentStatus.PUBLISHED) {
            throw new NotFoundException("Comment not found");
        }
        if (comment.getUser().getId().equals(me.getId())) {
            throw new BadRequestException("You can't mark your own comment as helpful");
        }
        var existing = votes.find(id, me.getId());
        boolean helpful;
        if (existing.isPresent()) {
            votes.delete(existing.get());
            comment.setHelpfulCount(Math.max(0, comment.getHelpfulCount() - 1));
            helpful = false;
        } else {
            votes.save(new CommentVote(comment, me));
            comment.setHelpfulCount(comment.getHelpfulCount() + 1);
            helpful = true;
        }
        comments.save(comment);
        return new HelpfulResult(helpful, comment.getHelpfulCount());
    }

    // ---- moderation (administrators) ----

    /** The comments waiting for a decision (status PENDING by default), oldest decision first is not needed: newest first. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public PageResult<CommentView> moderationQueue(String status, int page, int size, String adminUsername) {
        CommentStatus wanted;
        try {
            wanted = status == null || status.isBlank() ? CommentStatus.PENDING : CommentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown status '" + status + "'. Use PENDING, PUBLISHED or REJECTED");
        }
        if (page < 1) {
            throw new BadRequestException("page must be 1 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        User admin = user(adminUsername);
        Pageable pageable = PageRequest.of(page - 1, size);
        Page<Comment> found = comments.findByStatusOrderByCreatedAtDescIdDesc(wanted, pageable);
        Set<Long> voted = votedAmong(admin, found.getContent().stream().map(Comment::getId).toList());
        List<CommentView> items = found.getContent().stream().map(c -> view(c, admin, voted, List.of())).toList();
        return new PageResult<>(items, page, Math.max(1, found.getTotalPages()), found.getTotalElements());
    }

    /** Approves (publishes) or rejects a comment; the decision is added to its processing record. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CommentView moderate(long id, String action, String adminUsername) {
        User admin = user(adminUsername);
        Comment comment = comments.load(id).orElseThrow(() -> new NotFoundException("Comment not found"));
        CommentStatus status;
        String note;
        switch (action == null ? "" : action.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE" -> {
                status = CommentStatus.PUBLISHED;
                note = "Moderator review|PASSED|Approved by " + adminUsername;
            }
            case "REJECT" -> {
                status = CommentStatus.REJECTED;
                note = "Moderator review|FLAGGED|Rejected by " + adminUsername;
            }
            default -> throw new BadRequestException("action must be APPROVE or REJECT");
        }
        comment.moderate(status, comment.getProcessing() + "\n" + note);
        return view(comments.save(comment), admin, Set.of(), List.of());
    }

    // ---- account deletion ----

    /**
     * Removes everything a person wrote and marked, and the replies other people wrote to their comments, so the account itself
     * can be deleted. Called by {@code service/ProfileService} and {@code service/AdminUserService} before the user row goes.
     */
    @Transactional
    public void deleteAllOf(User user) {
        List<Long> own = comments.idsOfUser(user);
        List<Long> replies = own.isEmpty() ? List.of() : comments.idsOfRepliesTo(own);
        Set<Long> all = new LinkedHashSet<>(own);
        all.addAll(replies);
        // the person's marks on OTHER people's comments disappear, so those comments' stored helpful counts must go down with them
        List<Long> marked = new ArrayList<>(votes.commentIdsMarkedBy(user));
        marked.removeAll(all);
        if (!marked.isEmpty()) {
            comments.decrementHelpful(marked);
        }
        votes.deleteCastBy(user);
        if (!all.isEmpty()) {
            votes.deleteOnComments(all);
        }
        if (!replies.isEmpty()) {
            comments.deleteByIds(replies);
        }
        if (!own.isEmpty()) {
            comments.deleteByIds(own);
        }
    }

    // ---- helpers ----

    private List<CommentView> withReplies(List<Comment> topLevel, User me) {
        List<Long> ids = topLevel.stream().map(Comment::getId).toList();
        Map<Long, List<CommentView>> replies = ids.isEmpty() ? Map.of() : repliesOf(ids, me);
        Set<Long> voted = votedAmong(me, ids);
        return topLevel.stream().map(c -> view(c, me, voted, replies.getOrDefault(c.getId(), List.of()))).toList();
    }

    private Map<Long, List<CommentView>> repliesOf(List<Long> parentIds, User me) {
        List<Comment> replies = comments.findReplies(parentIds, me.getId());
        Set<Long> voted = votedAmong(me, replies.stream().map(Comment::getId).toList());
        Map<Long, List<CommentView>> byParent = new HashMap<>();
        for (Comment reply : replies) {
            byParent.computeIfAbsent(reply.getParent().getId(), k -> new ArrayList<>()).add(view(reply, me, voted, List.of()));
        }
        return byParent;
    }

    private Set<Long> votedAmong(User me, List<Long> commentIds) {
        return commentIds.isEmpty() ? Set.of() : new HashSet<>(votes.votedAmong(me.getId(), commentIds));
    }

    private static CommentView view(Comment c, User me, Set<Long> voted, List<CommentView> replies) {
        List<ProcessingStep> steps = CommentProcessor.decode(c.getProcessing()).stream()
                .map(s -> new ProcessingStep(s.name(), s.outcome(), s.detail())).toList();
        Sentiment sentiment = c.getSentiment();
        return new CommentView(c.getId(), c.getParent() == null ? null : c.getParent().getId(), c.getTmdbId(), c.getMediaType(),
                c.getUser().getUsername(), c.getUser().getDisplayName(), c.getBody(), c.getRating(), sentiment.name(), c.getSentimentScore(),
                c.getStatus().name(), c.getHelpfulCount(), voted.contains(c.getId()), c.getUser().getId().equals(me.getId()), c.getCreatedAt(),
                steps, replies);
    }

    private List<DiscussionSummary.Topic> topics(String type, long id) {
        Map<String, Integer> counts = new HashMap<>();
        for (String body : comments.recentBodies(type, id, PageRequest.of(0, TOPIC_SAMPLE))) {
            Set<String> inThisComment = new HashSet<>(); // a word repeated inside one comment counts once
            for (String word : CommentProcessor.tokenize(body)) {
                if (word.length() >= 4 && !STOP_WORDS.contains(word) && !CommentProcessor.LEXICON.containsKey(word) && !word.contains("'") && !word.contains("*")) {
                    inThisComment.add(word);
                }
            }
            inThisComment.forEach(w -> counts.merge(w, 1, Integer::sum));
        }
        return counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_TOPICS)
                .map(e -> new DiscussionSummary.Topic(e.getKey(), e.getValue()))
                .toList();
    }

    private Double tmdbRating(String type, long id) {
        try {
            double rating = catalog.detail(type, id).rating();
            return rating > 0 ? round1(rating) : null;
        } catch (AppException ex) {
            return null; // TMDB trouble must not break the discussion
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private void requireNotTooFast(User me) {
        if (comments.countByUserAndCreatedAtAfter(me, LocalDateTime.now().minusMinutes(1)) >= POSTS_PER_MINUTE) {
            throw new TooManyAttemptsException("You're posting too fast. Please wait a minute and try again");
        }
    }

    private void requireNotDuplicate(User me, String processedText) {
        if (comments.existsByUserAndBodyAndCreatedAtAfter(me, processedText, LocalDateTime.now().minus(DUPLICATE_WINDOW))) {
            throw new ConflictException("You already posted exactly this a moment ago");
        }
    }

    private static void requireTitle(String type, long id) {
        if (!TYPES.contains(type)) {
            throw new BadRequestException("Unknown type '" + type + "'. Use movie or tv");
        }
        if (id <= 0) {
            throw new BadRequestException("id must be positive");
        }
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
