package com.csb.cinescope.service;

import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.dto.WatchlistEntry;
import com.csb.cinescope.dto.WatchlistEntryRequest;
import com.csb.cinescope.dto.WatchlistSummary;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.entity.WatchlistItem;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.ConflictException;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;
import com.csb.cinescope.repository.WatchlistSpecifications;

/**
 * A user's personal watchlist: add or change a title's status / rating / note, look one up, remove it, list and count.
 *
 * <p>Called by {@code controller/WatchlistController} with the username from the login token; every operation is scoped to that
 * user (the repository has no way to read another user's items). Collaborators: {@code repository/} for storage and
 * {@code service/CatalogService} to check that a title really exists at TMDB and to take its snapshot (title, poster, year)
 * the first time it is added.
 *
 * <p>Rules: the title must exist at TMDB (404 otherwise); a rating (1-10) is only allowed with status WATCHED, and moving a
 * title to another status clears its rating; a title appears on a list once. The TMDB call is made BEFORE the database
 * transaction starts, so a slow TMDB never holds a database connection.
 */
@Service
public class WatchlistService {

    private static final Set<String> TYPES = Set.of("movie", "tv");
    private static final Set<String> SORTS = Set.of("added", "updated", "title", "rating");
    private static final int MAX_PAGE_SIZE = 50;

    /** The result of saving an entry; {@code created} tells the controller whether to answer 201 or 200. */
    public record SaveResult(WatchlistEntry entry, boolean created) {
    }

    private record Snapshot(String title, String posterPath, Integer year) {
    }

    private final WatchlistItemRepository items;
    private final UserRepository users;
    private final CatalogService catalog;
    private final TransactionTemplate transaction;

    public WatchlistService(WatchlistItemRepository items, UserRepository users, CatalogService catalog,
                            PlatformTransactionManager transactionManager) {
        this.items = items;
        this.users = users;
        this.catalog = catalog;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Adds the title (status WANT unless told otherwise) or replaces the status, rating and note of an existing entry. */
    public SaveResult save(String username, String type, long tmdbId, WatchlistEntryRequest request) {
        requireType(type);
        requireId(tmdbId);
        if (request.rating() != null && request.status() != WatchStatus.WATCHED) {
            throw new BadRequestException("A rating can only be given to a title you have watched");
        }
        String note = cleanNote(request.note());
        User user = requireUser(username);

        Snapshot snapshot = items.findByUserAndTmdbIdAndMediaType(user, tmdbId, type).isPresent() ? null : snapshotOf(type, tmdbId);

        try {
            return transaction.execute(status -> upsert(user, type, tmdbId, request.status(), request.rating(), note, snapshot));
        } catch (DataIntegrityViolationException race) {
            // Two requests added the same title at the same moment; the unique key rejected the second. The title is now on
            // the list, so apply this request as an update.
            return transaction.execute(status -> upsert(user, type, tmdbId, request.status(), request.rating(), note, null));
        }
    }

    @Transactional(readOnly = true)
    public WatchlistEntry get(String username, String type, long tmdbId) {
        requireType(type);
        return items.findByUserAndTmdbIdAndMediaType(requireUser(username), tmdbId, type)
                .map(WatchlistService::toEntry)
                .orElseThrow(() -> new NotFoundException("Not on your watchlist"));
    }

    @Transactional
    public void remove(String username, String type, long tmdbId) {
        requireType(type);
        if (items.deleteByUserAndTmdbIdAndMediaType(requireUser(username), tmdbId, type) == 0) {
            throw new NotFoundException("Not on your watchlist");
        }
    }

    /** One page of the user's list, optionally narrowed to a status and / or a media type, in the requested order. */
    @Transactional(readOnly = true)
    public PageResult<WatchlistEntry> list(String username, WatchStatus status, String type, String sort, int page, int size) {
        if (type != null) {
            requireType(type);
        }
        if (!SORTS.contains(sort)) {
            throw new BadRequestException("Unknown sort '" + sort + "'. Use added, updated, title or rating");
        }
        if (page < 1) {
            throw new BadRequestException("page must be 1 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        User user = requireUser(username);
        Page<WatchlistItem> result = items.findAll(WatchlistSpecifications.forUser(user, status, type),
                PageRequest.of(page - 1, size, sortFor(sort)));
        return new PageResult<>(result.getContent().stream().map(WatchlistService::toEntry).toList(), page,
                Math.max(1, result.getTotalPages()), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public WatchlistSummary summary(String username) {
        long want = 0;
        long watching = 0;
        long watched = 0;
        for (WatchlistItemRepository.StatusCount row : items.countByStatus(requireUser(username))) {
            switch (row.getStatus()) {
                case WANT -> want = row.getTotal();
                case WATCHING -> watching = row.getTotal();
                case WATCHED -> watched = row.getTotal();
            }
        }
        return new WatchlistSummary(want + watching + watched, want, watching, watched);
    }

    // ---- helpers ----

    private SaveResult upsert(User user, String type, long tmdbId, WatchStatus status, Integer rating, String note, Snapshot snapshot) {
        Optional<WatchlistItem> existing = items.findByUserAndTmdbIdAndMediaType(user, tmdbId, type);
        boolean created = existing.isEmpty();
        WatchlistItem item;
        if (created) {
            if (snapshot == null) {
                throw new ConflictException("The watchlist changed at the same time. Please try again");
            }
            item = new WatchlistItem(user, tmdbId, type, snapshot.title(), snapshot.posterPath(), snapshot.year());
        } else {
            item = existing.get();
        }
        item.update(status, rating, note);
        return new SaveResult(toEntry(items.saveAndFlush(item)), created);
    }

    /** Asks the catalogue about the title: a title that does not exist at TMDB cannot be added (404 from the gateway). */
    private Snapshot snapshotOf(String type, long tmdbId) {
        TitleDetail detail = catalog.detail(type, tmdbId);
        return new Snapshot(detail.title() != null ? detail.title() : "Untitled", detail.posterPath(), detail.year());
    }

    private User requireUser(String username) {
        return users.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private static void requireType(String type) {
        if (!TYPES.contains(type)) {
            throw new BadRequestException("Unknown type '" + type + "'. Use movie or tv");
        }
    }

    private static void requireId(long id) {
        if (id <= 0) {
            throw new BadRequestException("id must be positive");
        }
    }

    private static String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }

    private static Sort sortFor(String sort) {
        return switch (sort) {
            case "title" -> Sort.by(Sort.Order.asc("title").ignoreCase(), Sort.Order.desc("id"));
            case "rating" -> Sort.by(Sort.Order.desc("rating").nullsLast(), Sort.Order.desc("updatedAt"));
            case "updated" -> Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
            default -> Sort.by(Sort.Order.desc("addedAt"), Sort.Order.desc("id"));
        };
    }

    private static WatchlistEntry toEntry(WatchlistItem item) {
        return new WatchlistEntry(item.getTmdbId(), item.getMediaType(), item.getTitle(), item.getPosterPath(), item.getReleaseYear(),
                item.getStatus(), item.getRating(), item.getNote(), item.getAddedAt(), item.getUpdatedAt());
    }
}
