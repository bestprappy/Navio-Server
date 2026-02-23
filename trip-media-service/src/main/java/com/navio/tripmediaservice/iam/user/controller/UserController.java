package com.navio.tripmediaservice.iam.user.controller;

import com.navio.tripmediaservice.iam.user.dto.PreferenceUpdateRequest;
import com.navio.tripmediaservice.iam.user.dto.ProfileUpdateRequest;
import com.navio.tripmediaservice.iam.user.dto.UserResponse;
import com.navio.tripmediaservice.iam.user.dto.UserSyncRequest;
import com.navio.tripmediaservice.iam.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Internal endpoints ──────────────────────────────────────────────

    /**
     * Upsert user from Keycloak claims (called on every login).
     */
    @PostMapping("/internal/v1/users/sync")
    public ResponseEntity<UserResponse> syncUser(@Valid @RequestBody UserSyncRequest request) {
        return ResponseEntity.ok(userService.syncUser(request));
    }

    /**
     * Get user by ID (internal — used by other modules).
     */
    @GetMapping("/internal/v1/users/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    // ── Public endpoints (through NGINX) ────────────────────────────────

    /**
     * Get the current authenticated user's profile.
     */
    @GetMapping("/v1/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(userService.getCurrentUser(jwt.getSubject()));
    }

    /**
     * Update current user's profile fields.
     */
    @PatchMapping("/v1/me")
    public ResponseEntity<UserResponse> updateProfile(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody ProfileUpdateRequest request) {
        return ResponseEntity.ok(userService.updateProfile(jwt.getSubject(), request));
    }

    /**
     * Update current user's preferences.
     */
    @PatchMapping("/v1/me/preferences")
    public ResponseEntity<UserResponse> updatePreferences(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody PreferenceUpdateRequest request) {
        return ResponseEntity.ok(userService.updatePreferences(jwt.getSubject(), request));
    }
}
