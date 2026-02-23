package com.navio.tripmediaservice.iam.user.service;

import com.navio.tripmediaservice.exception.ResourceNotFoundException;
import com.navio.tripmediaservice.iam.user.dto.PreferenceUpdateRequest;
import com.navio.tripmediaservice.iam.user.dto.ProfileUpdateRequest;
import com.navio.tripmediaservice.iam.user.dto.UserResponse;
import com.navio.tripmediaservice.iam.user.dto.UserSyncRequest;
import com.navio.tripmediaservice.iam.user.model.preference.Preference;
import com.navio.tripmediaservice.iam.user.model.user.User;
import com.navio.tripmediaservice.iam.user.repository.UserRepository;
import com.navio.tripmediaservice.iam.user.util.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Upsert user from Keycloak claims.
     * Called on every login via {@code POST /internal/v1/users/sync}.
     */
    @Transactional
    public UserResponse syncUser(UserSyncRequest request) {
        User user = userRepository.findById(request.getId())
                .map(existing -> {
                    existing.setEmail(request.getEmail());
                    existing.setDisplayName(request.getDisplayName());
                    return existing;
                })
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setId(request.getId());
                    newUser.setEmail(request.getEmail());
                    newUser.setDisplayName(request.getDisplayName());
                    return newUser;
                });

        User saved = userRepository.save(user);
        return UserMapper.toResponse(saved);
    }

    /**
     * Get user by ID.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return UserMapper.toResponse(user);
    }

    /**
     * Get the current authenticated user's profile.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        return getUserById(userId);
    }

    /**
     * Update profile fields (displayName, avatarUrl, bio).
     */
    @Transactional
    public UserResponse updateProfile(String userId, ProfileUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio());
        }

        User saved = userRepository.save(user);
        return UserMapper.toResponse(saved);
    }

    /**
     * Update user preferences (theme, distanceUnit, locale, notifications).
     */
    @Transactional
    public UserResponse updatePreferences(String userId, PreferenceUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Preference prefs = user.getPreferences();
        if (prefs == null) {
            prefs = new Preference();
        }

        if (request.getTheme() != null) {
            prefs.setTheme(request.getTheme());
        }
        if (request.getDistanceUnit() != null) {
            prefs.setDistanceUnit(request.getDistanceUnit());
        }
        if (request.getLocale() != null) {
            prefs.setLocale(request.getLocale());
        }
        if (request.getNotifications() != null) {
            prefs.setNotifications(request.getNotifications());
        }

        user.setPreferences(prefs);
        User saved = userRepository.save(user);
        return UserMapper.toResponse(saved);
    }
}
