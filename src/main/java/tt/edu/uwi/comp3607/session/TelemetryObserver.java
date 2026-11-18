package tt.edu.uwi.comp3607.session;

import tt.edu.uwi.comp3607.model.TelemetryEvent;

/**
 * Receives each new event as soon as a {@link TelemetrySession} records it.
 *
 * Design pattern: Observer. TelemetrySession is the "subject". Any class that
 * implements this interface can subscribe with
 * {@link TelemetrySession#addObserver(TelemetryObserver)} and gets told about
 * every new event straight away. This means we can add features later (like a
 * live log or an on-screen counter) without changing the session class at all.
 *
 * Since it only has one method, you can write an observer as a lambda:
 * <pre>{@code
 * session.addObserver(event -> System.out.println(event));
 * }</pre>
 *
 * @author Kirese Tasha Maharaj
 * @see TelemetrySession
 */
public interface TelemetryObserver {

    /**
 * Handles an event the moment the session records it.
 *
 * This runs on whatever thread BlueJ used to send the event, so keep it quick.
 * If it throws an exception, the session catches it and prints it, so one broken
 * observer won't stop the others or crash BlueJ.
 *
 * @param event the event that was just recorded, never {@code null}
 */
    void onEvent(TelemetryEvent event);
}