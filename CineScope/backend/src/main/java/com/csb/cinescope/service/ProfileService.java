package com.csb.cinescope.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.cinescope.dto.AuthResponse;
import com.csb.cinescope.dto.ChangePasswordRequest;
import com.csb.cinescope.dto.DeleteAccountRequest;
import com.csb.cinescope.dto.UpdateProfileRequest;
import com.csb.cinescope.dto.UserResponse;
import com.csb.cinescope.entity.Role;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.ConflictException;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;
import com.csb.cinescope.security.PasswordPolicy;

/**
 * What a signed-in person can do with their own account: see it, change their name and country, change their password, and
 * delete the account. Called by {@code controller/ProfileController}; works on the account named by the login token, so a
 * person can only ever touch their own.
 *
 * <p>Actions that need proof of identity (changing the password, deleting the account) ask for the CURRENT password, so a
 * stolen but still-valid token alone is not enough. Wrong guesses count against the same lockout as login
 * ({@code LoginAttemptService}), so this cannot be used to guess the password. Changing the password bumps the account's
 * token version ({@code entity/User#changePassword}), which ends every session, and the answer carries a fresh token so
 * the person who changed it stays signed in.
 */
@Service
public class ProfileService {

    private final UserRepository users;
    private final WatchlistItemRepository watchlist;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService attempts;
    private final AuthService authService;
    private final CommentService commentService;

    public ProfileService(UserRepository users, WatchlistItemRepository watchlist, PasswordEncoder passwordEncoder,
                          LoginAttemptService attempts, AuthService authService, CommentService commentService) {
        this.users = users;
        this.watchlist = watchlist;
        this.passwordEncoder = passwordEncoder;
        this.attempts = attempts;
        this.authService = authService;
        this.commentService = commentService;
    }

    @Transactional(readOnly = true)
    public UserResponse get(String username) {
        return AuthService.toUserResponse(find(username));
    }

    @Transactional
    public UserResponse update(String username, UpdateProfileRequest request) {
        User user = find(username);
        user.updateProfile(request.displayName().trim(), request.region().trim().toUpperCase(Locale.ROOT));
        return AuthService.toUserResponse(users.save(user));
    }

    /** Changes the password after checking the current one; returns a new session because the old tokens no longer work. */
    @Transactional
    public AuthResponse changePassword(String username, ChangePasswordRequest request) {
        User user = find(username);
        requireCurrentPassword(user, request.currentPassword());
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BadRequestException("The new password must be different from the current one");
        }
        String identityProblem = PasswordPolicy.identityProblem(request.newPassword(), user.getUsername(), user.getEmail());
        if (identityProblem != null) {
            throw new BadRequestException(identityProblem);
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        return authService.sessionFor(users.save(user));
    }

    /** Deletes the account, its watchlist and its comments for good, after the password is confirmed. */
    @Transactional
    public void deleteAccount(String username, DeleteAccountRequest request) {
        User user = find(username);
        requireCurrentPassword(user, request.password());
        if (user.getRole() == Role.ADMIN && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new ConflictException("You are the only administrator. Make someone else an administrator before deleting this account");
        }
        commentService.deleteAllOf(user);
        watchlist.deleteByUser(user);
        users.delete(user);
    }

    private void requireCurrentPassword(User user, String password) {
        attempts.checkNotLocked(user.getUsername());
        if (!passwordEncoder.matches(password, user.getPassword())) {
            attempts.recordFailure(user.getUsername());
            // 400, not 401: a 401 would make the app think the login token expired and sign the person out.
            throw new BadRequestException("The current password is incorrect");
        }
        attempts.recordSuccess(user.getUsername());
    }

    private User find(String username) {
        return users.findByUsername(username).orElseThrow(() -> new NotFoundException("User not found"));
    }
}
