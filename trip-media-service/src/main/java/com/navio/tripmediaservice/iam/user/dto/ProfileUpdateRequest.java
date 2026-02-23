package com.navio.tripmediaservice.iam.user.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code PATCH /v1/me} — update profile fields.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileUpdateRequest {

    @Size(min = 1, max = 100)
    private String displayName;

    @Size(max = 2048)
    private String avatarUrl;

    @Size(max = 5000)
    private String bio;
}
