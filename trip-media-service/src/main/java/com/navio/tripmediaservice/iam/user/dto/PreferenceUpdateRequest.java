package com.navio.tripmediaservice.iam.user.dto;


import com.navio.tripmediaservice.iam.user.model.notification.Notification;
import com.navio.tripmediaservice.iam.user.model.preference.DistanceUnit;
import com.navio.tripmediaservice.iam.user.model.preference.Theme;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body for {@code PATCH /v1/me/preferences}.
 * All fields are optional — only non-null fields are applied.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceUpdateRequest {

    private Theme theme;
    private DistanceUnit distanceUnit;
    private String locale;
    private Notification notifications;
}
