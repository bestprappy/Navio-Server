package com.navio.trip.iam.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code POST /internal/v1/users/sync}.
 * Upserts a user profile from Keycloak JWT claims.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSyncRequest {

    @NotBlank
    private String id;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 1, max = 100)
    private String displayName;
}
