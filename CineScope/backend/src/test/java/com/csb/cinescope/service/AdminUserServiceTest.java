package com.csb.cinescope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.AdminCreateUserRequest;
import com.csb.cinescope.dto.AdminUpdateUserRequest;
import com.csb.cinescope.dto.AdminUserView;
import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.ConflictException;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;

/**
 * The rules of {@link AdminUserService} with the repositories mocked: the safety rules that stop administrators locking
 * everyone out, the order of deletion, defaults and normalisation. (Who may call the service at all is
 * {@code @PreAuthorize}, which only applies through Spring; {@code controller/AdminUserApiTest} tests that part.)
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository users;
    @Mock WatchlistItemRepository watchlist;
    @Mock PasswordEncoder encoder;
    @Mock CommentService commentService;

    AdminUserService service;
    User boss = new User("boss", "boss@example.com", "hash", "Boss", "IN", Role.ADMIN);
    User second = new User("second", "second@example.com", "hash", "Second", "IN", Role.ADMIN);
    User asha = new User("asha", "asha@example.com", "hash", "Asha", "IN");

    @BeforeEach
    void setUp() {
        service = new AdminUserService(users, watchlist, encoder, new CineScopeProperties("IN", "en-US"), commentService);
        ReflectionTestUtils.setField(boss, "id", 1L); // entities get their id from the database; here they are given one
        ReflectionTestUtils.setField(second, "id", 2L);
        ReflectionTestUtils.setField(asha, "id", 5L);
    }

    private static AdminUpdateUserRequest update(Role role, boolean enabled) {
        return new AdminUpdateUserRequest("  New Name ", "gb", role, enabled);
    }

    private void found(long id, User user) {
        when(users.findById(id)).thenReturn(Optional.of(user));
        org.mockito.Mockito.lenient().when(users.save(user)).thenReturn(user); // not every test reaches the save
    }

    // ---- listing ----

    @Test
    void listParametersAreValidatedBeforeAnythingIsRead() {
        assertThrows(BadRequestException.class, () -> service.list(null, null, null, 0, 10));
        assertThrows(BadRequestException.class, () -> service.list(null, null, null, 1, 0));
        assertThrows(BadRequestException.class, () -> service.list(null, null, null, 1, 51));
        org.mockito.Mockito.verifyNoInteractions(users, watchlist);
    }

    // ---- update ----

    @Test
    void anUpdateTrimsTheNameUppercasesTheCountryAndSavesAllFields() {
        found(5, asha);
        when(watchlist.countByUser(asha)).thenReturn(3L);

        AdminUserView view = service.update(5, update(Role.ADMIN, false), "boss");

        assertEquals("New Name", view.displayName());
        assertEquals("GB", view.region());
        assertEquals("ADMIN", view.role());
        assertFalse(view.enabled());
        assertEquals(3, view.watchlistCount());
        assertEquals(2, asha.getTokenVersion(), "a role change and a disabling each end the old sessions");
    }

    @Test
    void anUnchangedRoleAndEnabledStateDoNotEndSessions() {
        found(5, asha);

        service.update(5, update(Role.USER, true), "boss");

        assertEquals(0, asha.getTokenVersion());
    }

    @Test
    void youCannotDemoteOrDisableYourself() {
        found(1, boss);

        assertThrows(BadRequestException.class, () -> service.update(1, update(Role.USER, true), "boss"));
        assertThrows(BadRequestException.class, () -> service.update(1, update(Role.ADMIN, false), "boss"));
        verify(users, never()).save(any());
        assertEquals(Role.ADMIN, boss.getRole());
        assertTrue(boss.isEnabled());
    }

    @Test
    void theLastEnabledAdministratorCannotBeDemotedOrDisabledByAnother() {
        found(2, second);
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.update(2, update(Role.USER, true), "boss"));
        assertThrows(ConflictException.class, () -> service.update(2, update(Role.ADMIN, false), "boss"));
        verify(users, never()).save(any());
    }

    @Test
    void withAnotherEnabledAdministratorAnAdministratorCanBeDemoted() {
        found(2, second);
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);

        AdminUserView view = service.update(2, update(Role.USER, true), "boss");

        assertEquals("USER", view.role());
    }

    @Test
    void anUnknownAccountIs404() {
        when(users.findById(9L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.update(9, update(Role.USER, true), "boss"));
        assertThrows(NotFoundException.class, () -> service.delete(9, "boss"));
        assertThrows(NotFoundException.class, () -> service.get(9));
    }

    // ---- delete ----

    @Test
    void deletionRemovesTheCommentsAndWatchlistBeforeTheAccount() {
        found(5, asha);

        service.delete(5, "boss");

        InOrder order = inOrder(commentService, watchlist, users);
        order.verify(commentService).deleteAllOf(asha);
        order.verify(watchlist).deleteByUser(asha);
        order.verify(users).delete(asha);
    }

    @Test
    void youCannotDeleteYourselfAndTheLastAdministratorCannotBeDeleted() {
        found(1, boss);
        found(2, second);
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(BadRequestException.class, () -> service.delete(1, "boss"));
        assertThrows(ConflictException.class, () -> service.delete(2, "boss"));
        verify(users, never()).delete(any(User.class));
        verify(watchlist, never()).deleteByUser(any());
    }

    @Test
    void aDisabledAdministratorCanBeDeletedEvenIfItIsTheOnlyOtherOne() {
        second.setEnabled(false);
        found(2, second);

        service.delete(2, "boss");

        verify(users).delete(second);
        verify(users, never()).countByRoleAndEnabledTrue(any());
    }

    // ---- create ----

    private static AdminCreateUserRequest create(String username, String email, String password, String region, Role role) {
        return new AdminCreateUserRequest(username, email, " New Person ", password, region, role);
    }

    @Test
    void creatingNormalisesAndHashesAndDefaultsTheCountry() {
        when(encoder.encode("Violet-Rain-77!")).thenReturn("ENCODED");
        when(users.save(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 9L);
            return saved;
        });

        AdminUserView view = service.create(create(" New.Hire ", " New.Hire@Example.COM ", "Violet-Rain-77!", null, Role.ADMIN), "boss");

        assertEquals("new.hire", view.username());
        assertEquals("new.hire@example.com", view.email());
        assertEquals("New Person", view.displayName());
        assertEquals("IN", view.region());
        assertEquals("ADMIN", view.role());
        assertTrue(view.enabled());
    }

    @Test
    void creatingRefusesDuplicatesAndPasswordsContainingTheName() {
        when(users.existsByUsername("asha")).thenReturn(true);
        when(users.existsByUsername("fresh")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.create(create("Asha", "x@example.com", "Violet-Rain-77!", "IN", Role.USER), "boss"));
        assertThrows(ConflictException.class, () -> service.create(create("fresh", "taken@example.com", "Violet-Rain-77!", "IN", Role.USER), "boss"));
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.create(create("fresh", "x@example.com", "Mine-fresh-77!", "IN", Role.USER), "boss"));
        assertEquals("password must not contain your username", ex.getMessage());
        verify(users, never()).save(any());
    }
}
