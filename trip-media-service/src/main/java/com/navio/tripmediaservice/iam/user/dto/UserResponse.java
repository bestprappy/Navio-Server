package com.navio.tripmediaservice.iam.user.dto;

import com.navio.tripmediaservice.iam.user.model.preference.Preference;
import com.navio.tripmediaservice.iam.user.model.user.UserRole;
import com.navio.tripmediaservice.iam.user.model.user.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Read-only response DTO for user profile data.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String id;
    private String email;
    private String displayName;
    private String avatarUrl;
    private UserRole role;
    private UserStatus status;
    private String bio;
    private Preference preferences;
    private Instant createdAt;
    private Instant updatedAt;
}
