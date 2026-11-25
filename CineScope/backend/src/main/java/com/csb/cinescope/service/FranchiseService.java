package com.csb.cinescope.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.FranchiseAddResult;
import com.csb.cinescope.dto.FranchiseView;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.entity.WatchlistItem;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;
import com.csb.cinescope.tmdb.TmdbGateway;
import com.csb.cinescope.tmdb.TmdbQuery;

import tools.jackson.databind.JsonNode;

/**
 * The franchise tracker: the series a film belongs to (a TMDB "collection", such as the Avengers or Harry Potter films), which of
 * its films the person has watched, and a one-step way to put the rest on their watchlist. TMDB groups films into collections
 * per series, so there is no single "all Marvel films" collection.
 *
 * <p>Both TMDB lookups go through {@code tmdb/TmdbGateway} (cached for an hour). A film that is in no series simply has no
 * franchise. Adding never changes a title that is already on the list (its status, rating and note stay as they are) and never
 * adds a film that is not out yet. Called by {@code controller/FranchiseController}; tested by {@code FranchiseApiTest}.
 */
@Service
public class FranchiseService {

    /** A series longer than this is cut: the page shows a row of posters, and a runaway collection must not add hundreds of rows. */
    static final int MAX_PARTS = 40;

    private final TmdbGateway tmdb;
    private final CineScopeProperties properties;
    private final WatchlistItemRepository items;
    private final UserRepository users;

    public FranchiseService(TmdbGateway tmdb, CineScopeProperties properties, WatchlistItemRepository items, UserRepository users) {
        this.tmdb = tmdb;
        this.properties = properties;
        this.items = items;
        this.users = users;
    }

    /** The series that film {@code id} belongs to, with this person's progress; empty when the film is not part of a series. */
    @Transactional(readOnly = true)
    public Optional<FranchiseView> ofFilm(String username, long id) {
        JsonNode film = tmdb.detail("/movie/" + id, language());
        JsonNode collection = film.path("belongs_to_collection");
        if (!collection.path("id").isNumber()) {
            return Optional.empty();
        }
        long collectionId = collection.path("id").asLong();
        JsonNode root = tmdb.detail("/collection/" + collectionId, language());
        User user = user(username);
        Map<Long, WatchStatus> mine = new HashMap<>();
        items.findTagged(user, "movie").forEach(t -> mine.put(t.getTmdbId(), t.getStatus()));

        LocalDate today = LocalDate.now();
        List<FranchiseView.Part> parts = new ArrayList<>();
        for (JsonNode part : parts(root)) {
            String date = text(part, "release_date");
            WatchStatus status = mine.get(part.path("id").asLong());
            parts.add(new FranchiseView.Part(part.path("id").asLong(), text(part, "title") != null ? text(part, "title") : "Untitled", yearOf(date), date,
                    text(part, "poster_path"), isReleased(date, today), status == null ? null : status.name()));
        }
        parts.sort(Comparator.comparing(FranchiseView.Part::releaseDate, Comparator.nullsLast(Comparator.naturalOrder())));

        int released = (int) parts.stream().filter(FranchiseView.Part::released).count();
        int watched = (int) parts.stream().filter(p -> WatchStatus.WATCHED.name().equals(p.status())).count();
        int missing = (int) parts.stream().filter(p -> p.released() && p.status() == null).count();
        return Optional.of(new FranchiseView(collectionId, text(root, "name") != null ? text(root, "name") : "Collection", text(root, "poster_path"),
                parts.size(), released, watched, missing, parts));
    }

    /** Puts every released film of the collection that is not on the person's list on it, as want-to-watch. */
    @Transactional
    public FranchiseAddResult addRest(String username, long collectionId) {
        JsonNode root = tmdb.detail("/collection/" + collectionId, language());
        User user = user(username);
        LocalDate today = LocalDate.now();
        int added = 0;
        int already = 0;
        int notReleased = 0;
        for (JsonNode part : parts(root)) {
            String date = text(part, "release_date");
            long id = part.path("id").asLong();
            if (!isReleased(date, today)) {
                notReleased++;
            } else if (items.findByUserAndTmdbIdAndMediaType(user, id, "movie").isPresent()) {
                already++;
            } else {
                String title = text(part, "title") != null ? text(part, "title") : "Untitled";
                items.save(new WatchlistItem(user, id, "movie", title, text(part, "poster_path"), yearOf(date)));
                added++;
            }
        }
        return new FranchiseAddResult(added, already, notReleased);
    }

    private List<JsonNode> parts(JsonNode root) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode part : root.path("parts")) {
            if (part.path("id").isNumber() && out.size() < MAX_PARTS) {
                out.add(part);
            }
        }
        if (out.isEmpty() && !root.path("id").isNumber()) {
            throw new NotFoundException("That collection was not found");
        }
        return out;
    }

    private java.util.SortedMap<String, String> language() {
        return TmdbQuery.create().with("language", properties.language()).build();
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    private static boolean isReleased(String date, LocalDate today) {
        try {
            return date != null && !LocalDate.parse(date).isAfter(today);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static Integer yearOf(String date) {
        return date != null && date.length() >= 4 && date.substring(0, 4).chars().allMatch(Character::isDigit) ? Integer.valueOf(date.substring(0, 4)) : null;
    }
}
