package tt.edu.uwi.comp3607.session;

import tt.edu.uwi.comp3607.model.TelemetryEvent;

/**
 * DESIGN PATTERN: Observer.
 *
 * Anything that wants to react the moment an event is recorded
 * (a live log file, an on-screen counter, ...) implements this and
 * subscribes to a TelemetrySession.
 */
public interface TelemetryObserver {

    void onEvent(TelemetryEvent event);
}