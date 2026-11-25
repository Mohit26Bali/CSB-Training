package com.csb.cinescope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

import com.csb.cinescope.dto.AuthResponse;
import com.csb.cinescope.dto.ChangePasswordRequest;
import com.csb.cinescope.dto.DeleteAccountRequest;
import com.csb.cinescope.dto.UpdateProfileRequest;
import com.csb.cinescope.dto.UserResponse;
import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.ConflictException;
import com.csb.cinescope.exception.TooManyAttemptsException;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;

/**
 * The rules of {@link ProfileService} with everything around it mocked: proof of the current password (and the lockout that
 * stops guessing it), the new-password rules, what changing the password does to the token version, and the order and guard
 * of account deletion.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock UserRepository users;
    @Mock WatchlistItemRepository watchlist;
    @Mock PasswordEncoder encoder;
    @Mock LoginAttemptService attempts;
    @Mock AuthService authService;
    @Mock CommentService commentService;

    ProfileService service;
    User asha = new User("asha", "asha@example.com", "OLDHASH", "Asha", "IN");

    @BeforeEach
    void setUp() {
        service = new ProfileService(users, watchlist, encoder, attempts, authService, commentService);
        org.mockito.Mockito.lenient().when(users.findByUsername("asha")).thenReturn(Optional.of(asha));
    }

    @Test
    void updateTrimsTheNameAndUppercasesTheCountry() {
        when(users.save(asha)).thenReturn(asha);

        UserResponse response = service.update("asha", new UpdateProfileRequest("  Asha R.  ", " us "));

        assertEquals("Asha R.", response.displayName());
        assertEquals("US", response.region());
        assertEquals("asha@example.com", response.email(), "the email is not part of the update");
    }

    // ---- change password ----

    @Test
    void aCorrectCurrentPasswordChangesTheHashEndsOldSessionsAndReturnsAFreshOne() {
        when(encoder.matches("Secret-Pass-1!", "OLDHASH")).thenReturn(true);
        when(encoder.matches("Violet-Rain-77!", "OLDHASH")).thenReturn(false);
        when(encoder.encode("Violet-Rain-77!")).thenReturn("NEWHASH");
        when(users.save(asha)).thenReturn(asha);
        AuthResponse fresh = new AuthResponse("new-token", 60, "asha", "Asha", "IN", "USER");
        when(authService.sessionFor(asha)).thenReturn(fresh);

        AuthResponse response = service.changePassword("asha", new ChangePasswordRequest("Secret-Pass-1!", "Violet-Rain-77!"));

        assertEquals(fresh, response);
        assertEquals("NEWHASH", asha.getPassword());
        assertEquals(1, asha.getTokenVersion());
        verify(attempts).recordSuccess("asha");
    }

    @Test
    void aWrongCurrentPasswordIsCountedAsAFailedAttemptAndChangesNothing() {
        when(encoder.matches("wrong", "OLDHASH")).thenReturn(false);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> service.changePassword("asha", new ChangePasswordRequest("wrong", "Violet-Rain-77!")));

        assertEquals("The current password is incorrect", ex.getMessage());
        verify(attempts).recordFailure("asha");
        verify(users, never()).save(any());
        assertEquals("OLDHASH", asha.getPassword());
        assertEquals(0, asha.getTokenVersion());
    }

    @Test
    void whenTheAccountIsLockedNothingIsCheckedAtAll() {
        org.mockito.Mockito.doThrow(new TooManyAttemptsException("Too many failed attempts. Try again in 15 minutes")).when(attempts).checkNotLocked("asha");

        assertThrows(TooManyAttemptsException.class, () -> service.changePassword("asha", new ChangePasswordRequest("Secret-Pass-1!", "Violet-Rain-77!")));
        assertThrows(TooManyAttemptsException.class, () -> service.deleteAccount("asha", new DeleteAccountRequest("Secret-Pass-1!")));
        org.mockito.Mockito.verifyNoInteractions(encoder);
    }

    @Test
    void theNewPasswordMustDifferFromTheCurrentAndNotContainTheUsername() {
        when(encoder.matches("Secret-Pass-1!", "OLDHASH")).thenReturn(true);

        BadRequestException same = assertThrows(BadRequestException.class, () -> service.changePassword("asha", new ChangePasswordRequest("Secret-Pass-1!", "Secret-Pass-1!")));
        assertEquals("The new password must be different from the current one", same.getMessage());

        when(encoder.matches("Rain-asha-77!", "OLDHASH")).thenReturn(false);
        BadRequestException named = assertThrows(BadRequestException.class, () -> service.changePassword("asha", new ChangePasswordRequest("Secret-Pass-1!", "Rain-asha-77!")));
        assertEquals("password must not contain your username", named.getMessage());
        verify(encoder, never()).encode(any());
    }

    // ---- delete ----

    @Test
    void deletionNeedsThePasswordAndRemovesTheCommentsAndWatchlistBeforeTheAccount() {
        when(encoder.matches("Secret-Pass-1!", "OLDHASH")).thenReturn(true);

        service.deleteAccount("asha", new DeleteAccountRequest("Secret-Pass-1!"));

        InOrder order = inOrder(commentService, watchlist, users);
        order.verify(commentService).deleteAllOf(asha);
        order.verify(watchlist).deleteByUser(asha);
        order.verify(users).delete(asha);
    }

    @Test
    void aWrongPasswordDeletesNothing() {
        when(encoder.matches("nope", "OLDHASH")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.deleteAccount("asha", new DeleteAccountRequest("nope")));

        verify(attempts).recordFailure("asha");
        verify(users, never()).delete(any(User.class));
        verify(watchlist, never()).deleteByUser(any());
    }

    @Test
    void theOnlyEnabledAdministratorCannotDeleteTheirOwnAccount() {
        User boss = new User("boss", "boss@example.com", "BOSSHASH", "Boss", "IN", Role.ADMIN);
        when(users.findByUsername("boss")).thenReturn(Optional.of(boss));
        when(encoder.matches("Secret-Pass-1!", "BOSSHASH")).thenReturn(true);
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> service.deleteAccount("boss", new DeleteAccountRequest("Secret-Pass-1!")));

        verify(users, never()).delete(any(User.class));
        when(users.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);
        service.deleteAccount("boss", new DeleteAccountRequest("Secret-Pass-1!")); // with another administrator it is allowed
        verify(users).delete(boss);
    }
}
