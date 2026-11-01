package com.jobradar.match.notify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class QuietHoursTest {
    private static final LocalTime T22 = LocalTime.of(22, 0);
    private static final LocalTime T07 = LocalTime.of(7, 0);

    @Test
    void overnightWindow() {
        assertThat(Notifier.inQuietHours(T22, T07, LocalTime.of(23, 30))).isTrue();
        assertThat(Notifier.inQuietHours(T22, T07, LocalTime.of(6, 59))).isTrue();
        assertThat(Notifier.inQuietHours(T22, T07, LocalTime.of(7, 0))).isFalse();
        assertThat(Notifier.inQuietHours(T22, T07, LocalTime.of(12, 0))).isFalse();
    }

    @Test
    void sameDayWindowAndUnset() {
        assertThat(Notifier.inQuietHours(LocalTime.of(13, 0), LocalTime.of(14, 0), LocalTime.of(13, 30))).isTrue();
        assertThat(Notifier.inQuietHours(null, T07, LocalTime.of(3, 0))).isFalse();
    }
}
