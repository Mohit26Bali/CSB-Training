package com.csb.cinescope.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.csb.cinescope.dto.AuthResponse;
import com.csb.cinescope.dto.ChangePasswordRequest;
import com.csb.cinescope.dto.DeleteAccountRequest;
import com.csb.cinescope.dto.UpdateProfileRequest;
import com.csb.cinescope.dto.UserResponse;
import com.csb.cinescope.service.ProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * A signed-in person's own account. No logic here; {@code service/ProfileService} does the work on the account named by the
 * login token ({@code Authentication#getName}), so nobody can reach anyone else's through these URLs.
 *
 * <pre>
 * GET    /api/profile            my details
 * PUT    /api/profile            change my display name and country
 * PUT    /api/profile/password   change my password (needs the current one); answers with a new login token
 * DELETE /api/profile            delete my account and watchlist for good (needs my password)
 * </pre>
 * The frontend page is pages/ProfilePage.
 */
@Tag(name = "Profile", description = "My own account (login required)")
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profile;

    public ProfileController(ProfileService profile) {
        this.profile = profile;
    }

    @Operation(summary = "My details")
    @GetMapping
    public UserResponse get(Authentication authentication) {
        return profile.get(authentication.getName());
    }

    @Operation(summary = "Change my name and country")
    @PutMapping
    public UserResponse update(@Valid @RequestBody UpdateProfileRequest request, Authentication authentication) {
        return profile.update(authentication.getName(), request);
    }

    @Operation(summary = "Change my password", description = "Needs the current password. All earlier login tokens stop working; the answer holds a new one.")
    @PutMapping("/password")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        return profile.changePassword(authentication.getName(), request);
    }

    @Operation(summary = "Delete my account", description = "Permanent: removes the account and its watchlist. Needs the password. The last administrator cannot delete themselves.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Valid @RequestBody DeleteAccountRequest request, Authentication authentication) {
        profile.deleteAccount(authentication.getName(), request);
    }
}
