package com.navio.tripmediaservice.iam.user.model.preference;


import com.navio.tripmediaservice.iam.user.model.notification.Notification;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

@Setter
@Getter
@NoArgsConstructor
public class Preference implements Serializable {

    private Theme theme = Theme.LIGHT;
    private DistanceUnit distanceUnit = DistanceUnit.MILES;
    private Locale locale = Locale.US;
    private Notification notifications = new Notification();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Preference that = (Preference) o;
        return Objects.equals(theme, that.theme) &&
                Objects.equals(distanceUnit, that.distanceUnit) &&
                Objects.equals(locale, that.locale) &&
                Objects.equals(notifications, that.notifications);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theme, distanceUnit, locale, notifications);
    }

    @Override
    public String toString() {
        return "Preference{" +
                "theme=" + theme +
                ", distanceUnit=" + distanceUnit +
                ", locale=" + locale +
                ", notifications=" + notifications +
                '}';
    }
}
