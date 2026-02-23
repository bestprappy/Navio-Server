package com.navio.tripmediaservice.iam.user.util;

import com.navio.tripmediaservice.iam.user.dto.UserResponse;
import com.navio.tripmediaservice.iam.user.model.user.User;

/**
 * Maps between User entity and DTOs.
 */
public final class UserMapper {

    private UserMapper() {
        // utility class
    }

    public static UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .bio(user.getBio())
                .preferences(user.getPreferences())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
