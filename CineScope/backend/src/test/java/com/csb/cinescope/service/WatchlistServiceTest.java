package com.csb.cinescope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;

import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.dto.WatchlistEntry;
import com.csb.cinescope.dto.WatchlistEntryRequest;
import com.csb.cinescope.dto.WatchlistSummary;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.entity.WatchlistItem;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;

/**
 * Unit tests of the watchlist rules in {@link WatchlistService}, with the repositories and the catalogue mocked: what is
 * stored when a title is added, when TMDB is (and is not) consulted, the rating rule, validation, paging and counts.
 * No Spring, no database, no network.
 */
@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock WatchlistItemRepository items;
    @Mock UserRepository users;
    @Mock CatalogService catalog;

    WatchlistService service;
    User ravi = new User("ravi", "ravi@example.com", "hash", "Ravi", "IN");

    @BeforeEach
    void setUp() {
        service = new WatchlistService(items, users, catalog, mock(PlatformTransactionManager.class));
        org.mockito.Mockito.lenient().when(users.findByUsername("ravi")).thenReturn(Optional.of(ravi));
    }

    private TitleDetail detail(String title, String poster, Integer year) {
        return new TitleDetail(550, "movie", title, null, null, poster, null, year, null, null, 0, 0, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, false);
    }

    private WatchlistItem existing() {
        WatchlistItem item = new WatchlistItem(ravi, 550, "movie", "Fight Club", "/fc.jpg", 1999);
        item.update(WatchStatus.WATCHED, 9, "great");
        return item;
    }

    private void saveReturnsItsArgument() {
        when(items.saveAndFlush(any(WatchlistItem.class))).thenAnswer(call -> call.getArgument(0));
    }

    // ---- adding and changing ----

    @Test
    void addingANewTitleTakesASnapshotFromTheCatalogue() {
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 550L, "movie")).thenReturn(Optional.empty());
        when(catalog.detail("movie", 550)).thenReturn(detail("Fight Club", "/fc.jpg", 1999));
        saveReturnsItsArgument();

        WatchlistService.SaveResult result = service.save("ravi", "movie", 550, new WatchlistEntryRequest(WatchStatus.WANT, null, null));

        assertTrue(result.created());
        assertEquals("Fight Club", result.entry().title());
        assertEquals("/fc.jpg", result.entry().posterPath());
        assertEquals(1999, result.entry().year());
        assertEquals(WatchStatus.WANT, result.entry().status());
        assertEquals("movie", result.entry().type());
        assertEquals(550, result.entry().tmdbId());
    }

    @Test
    void changingAnExistingEntryDoesNotAskTmdbAgain() {
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 550L, "movie")).thenReturn(Optional.of(existing()));
        saveReturnsItsArgument();

        WatchlistService.SaveResult result = service.save("ravi", "movie", 550, new WatchlistEntryRequest(WatchStatus.WATCHING, null, "halfway"));

        assertFalse(result.created());
        assertEquals(WatchStatus.WATCHING, result.entry().status());
        assertEquals("halfway", result.entry().note());
        assertEquals("Fight Club", result.entry().title()); // the stored snapshot is kept
        verifyNoInteractions(catalog);
    }

    @Test
    void moveToAnotherStatusWithoutARatingClearsTheOldRating() {
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 550L, "movie")).thenReturn(Optional.of(existing()));
        saveReturnsItsArgument();

        WatchlistEntry entry = service.save("ravi", "movie", 550, new WatchlistEntryRequest(WatchStatus.WANT, null, null)).entry();

        assertNull(entry.rating());
        assertNull(entry.note());
    }

    @Test
    void aRatingIsAcceptedForAWatchedTitle() {
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 550L, "movie")).thenReturn(Optional.of(existing()));
        saveReturnsItsArgument();

        WatchlistEntry entry = service.save("ravi", "movie", 550, new WatchlistEntryRequest(WatchStatus.WATCHED, 7, null)).entry();

        assertEquals(7, entry.rating());
    }

    @Test
    void aRatingWithoutStatusWatchedIsRejectedBeforeAnythingIsStored() {
        for (WatchStatus notWatched : new WatchStatus[] { WatchStatus.WANT, WatchStatus.WATCHING }) {
            assertThrows(BadRequestException.class, () -> service.save("ravi", "movie", 550, new WatchlistEntryRequest(notWatched, 8, null)));
        }
        verify(items, never()).saveAndFlush(any());
        verifyNoInteractions(catalog);
    }

    @Test
    void theNoteIsTrimmedAndABlankNoteIsStoredAsNothing() {
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 550L, "movie")).thenReturn(Optional.of(existing()));
        saveReturnsItsArgument();

        assertEquals("trimmed", service.save("ravi", "movie", 550, new WatchlistEntryRequest(WatchStatus.WANT, null, "  trimmed  ")).entry().note());
        assertNull(service.save("ravi", "movie", 550, new WatchlistEntryRequest(WatchStatus.WANT, null, "    ")).entry().note());
    }

    @Test
    void aTitleWithoutAnyDataStillGetsADisplayableName() {
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 550L, "movie")).thenReturn(Optional.empty());
        when(catalog.detail("movie", 550)).thenReturn(detail(null, null, null));
        saveReturnsItsArgument();

        WatchlistEntry entry = service.save("ravi", "movie", 550, new WatchlistEntryRequest(WatchStatus.WANT, null, null)).entry();

        assertEquals("Untitled", entry.title());
        assertNull(entry.posterPath());
        assertNull(entry.year());
    }

    @Test
    void aTitleThatDoesNotExistAtTmdbCannotBeAdded() {
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 999L, "movie")).thenReturn(Optional.empty());
        when(catalog.detail("movie", 999)).thenThrow(new NotFoundException("Not found"));

        assertThrows(NotFoundException.class, () -> service.save("ravi", "movie", 999, new WatchlistEntryRequest(WatchStatus.WANT, null, null)));
        verify(items, never()).saveAndFlush(any());
    }

    @Test
    void twoSimultaneousAddsEndAsOneEntryThatIsThenUpdated() {
        WatchlistItem winner = new WatchlistItem(ravi, 550, "movie", "Fight Club", "/fc.jpg", 1999);
        // pre-check: empty; first upsert: empty (so it creates and collides); retry: the other request's row is there
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 550L, "movie"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(winner));
        when(catalog.detail("movie", 550)).thenReturn(detail("Fight Club", "/fc.jpg", 1999));
        when(items.saveAndFlush(any(WatchlistItem.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"))
                .thenAnswer(call -> call.getArgument(0));

        WatchlistService.SaveResult result = service.save("ravi", "movie", 550, new WatchlistEntryRequest(WatchStatus.WATCHING, null, null));

        assertFalse(result.created()); // by then the title was already on the list
        assertEquals(WatchStatus.WATCHING, result.entry().status());
        verify(items, times(2)).saveAndFlush(any());
    }

    @Test
    void invalidTypeOrIdIsRejected() {
        WatchlistEntryRequest request = new WatchlistEntryRequest(WatchStatus.WANT, null, null);
        assertThrows(BadRequestException.class, () -> service.save("ravi", "book", 1, request));
        assertThrows(BadRequestException.class, () -> service.save("ravi", "movie", 0, request));
        assertThrows(BadRequestException.class, () -> service.get("ravi", "book", 1));
        assertThrows(BadRequestException.class, () -> service.remove("ravi", "book", 1));
        verifyNoInteractions(catalog);
    }

    // ---- reading and removing ----

    @Test
    void getReturnsTheEntryOrNotFound() {
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 550L, "movie")).thenReturn(Optional.of(existing()));
        when(items.findByUserAndTmdbIdAndMediaType(ravi, 1L, "movie")).thenReturn(Optional.empty());

        WatchlistEntry entry = service.get("ravi", "movie", 550);

        assertEquals(WatchStatus.WATCHED, entry.status());
        assertEquals(9, entry.rating());
        assertThrows(NotFoundException.class, () -> service.get("ravi", "movie", 1));
    }

    @Test
    void removeDeletesTheEntryOrReportsThatItWasNotThere() {
        when(items.deleteByUserAndTmdbIdAndMediaType(ravi, 550L, "movie")).thenReturn(1L);
        when(items.deleteByUserAndTmdbIdAndMediaType(ravi, 1L, "movie")).thenReturn(0L);

        service.remove("ravi", "movie", 550);

        assertThrows(NotFoundException.class, () -> service.remove("ravi", "movie", 1));
    }

    @Test
    void anUnknownUserIsNotFound() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get("ghost", "movie", 550));
        assertThrows(NotFoundException.class, () -> service.summary("ghost"));
    }

    // ---- list and summary ----

    @SuppressWarnings("unchecked")
    @Test
    void listPagesAreOneBasedAndEntriesAreMapped() {
        when(items.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existing()), PageRequest.of(1, 20), 21));

        PageResult<WatchlistEntry> page = service.list("ravi", WatchStatus.WATCHED, "movie", "added", 2, 20);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(items).findAll(any(Specification.class), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber()); // page 2 for the client is index 1 for the database
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals("addedAt: DESC,id: DESC", pageable.getValue().getSort().toString());
        assertEquals(2, page.page());
        assertEquals(2, page.totalPages());
        assertEquals(21, page.totalResults());
        assertEquals("Fight Club", page.items().get(0).title());
    }

    @SuppressWarnings("unchecked")
    @Test
    void everySortOptionMapsToTheRightOrdering() {
        when(items.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.list("ravi", null, null, "title", 1, 10);
        service.list("ravi", null, null, "rating", 1, 10);
        service.list("ravi", null, null, "updated", 1, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(items, times(3)).findAll(any(Specification.class), pageable.capture());
        List<String> sorts = pageable.getAllValues().stream().map(p -> p.getSort().toString()).toList();
        assertTrue(sorts.get(0).startsWith("title: ASC"));
        assertTrue(sorts.get(1).startsWith("rating: DESC"));
        assertTrue(sorts.get(2).startsWith("updatedAt: DESC"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void anEmptyListStillReportsOnePage() {
        when(items.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        PageResult<WatchlistEntry> page = service.list("ravi", null, null, "added", 1, 20);

        assertTrue(page.items().isEmpty());
        assertEquals(1, page.totalPages());
        assertEquals(0, page.totalResults());
    }

    @Test
    void invalidListParametersAreRejectedBeforeTheDatabaseIsAsked() {
        assertThrows(BadRequestException.class, () -> service.list("ravi", null, "book", "added", 1, 20));
        assertThrows(BadRequestException.class, () -> service.list("ravi", null, null, "zzz", 1, 20));
        assertThrows(BadRequestException.class, () -> service.list("ravi", null, null, "added", 0, 20));
        assertThrows(BadRequestException.class, () -> service.list("ravi", null, null, "added", 1, 0));
        assertThrows(BadRequestException.class, () -> service.list("ravi", null, null, "added", 1, 51));
        verifyNoInteractions(items);
    }

    @Test
    void summaryCountsEachStatusAndTheTotal() {
        when(items.countByStatus(ravi)).thenReturn(List.of(count(WatchStatus.WANT, 4), count(WatchStatus.WATCHED, 2)));

        WatchlistSummary summary = service.summary("ravi");

        assertEquals(new WatchlistSummary(6, 4, 0, 2), summary);
    }

    private static WatchlistItemRepository.StatusCount count(WatchStatus status, long total) {
        return new WatchlistItemRepository.StatusCount() {
            @Override
            public WatchStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    @Test
    void theCatalogueIsNeverConsultedByReadsOrRemoves() {
        when(items.findByUserAndTmdbIdAndMediaType(any(), anyLong(), anyString())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.get("ravi", "tv", 1));
        verify(catalog, never()).detail(eq("tv"), anyLong());
    }
}
