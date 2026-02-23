package com.navio.tripmediaservice.iam.user.model.notification;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

/**
 * Notification preferences embedded in the user's JSONB preferences column.
 * Not a JPA entity — serialized/deserialized by Jackson via {@code @JdbcTypeCode(SqlTypes.JSON)}.
 */
@Setter
@Getter
@ToString
@NoArgsConstructor
public class Notification implements Serializable {

    private boolean inApp = true;
    private boolean email = false;
    private boolean push = false;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Notification that = (Notification) o;
        return inApp == that.inApp && email == that.email && push == that.push;
    }

    @Override
    public int hashCode() {
        return Objects.hash(inApp, email, push);
    }
}
