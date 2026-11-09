package tt.edu.uwi.comp3607;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock that only moves when a test tells it to,
 * so time-based results are exact and repeatable.
 */
public class TestClock extends Clock {

    private Instant now;

    public TestClock(Instant start) {
        this.now = start;
    }

    public static TestClock atEpoch() {
        return new TestClock(Instant.parse("2026-11-09T10:00:00Z"));
    }

    public void advance(Duration amount) {
        now = now.plus(amount);
    }

    public void advanceSeconds(long seconds) {
        advance(Duration.ofSeconds(seconds));
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}