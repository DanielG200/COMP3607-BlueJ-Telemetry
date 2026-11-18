 package tt.edu.uwi.comp3607.model;

/**
 * Lists every kind of action our plugin can record while a student is working in BlueJ.
 *
 * Every {@link TelemetryEvent} has one of these types. We grouped them by the
 * areas in our project scope: the session, the project/class lifecycle, the
 * editor, compiling and running code.
 *
 * If we want to track something new, we just add a value here and a matching
 * method in {@link EventFactory}. The JSON exporter writes every type
 * automatically, so nothing else has to change.
 *
 * Used by {@link EventFactory} when making events, and by
 * {@link tt.edu.uwi.comp3607.report.ReportAggregator} when counting them.
 *
 * @author Kirese Tasha Maharaj
 * @see TelemetryEvent
 * @see EventFactory
 */
public enum EventType {

    // Session
   /** BlueJ has opened and a new session has started. */
SESSION_STARTED,

/** BlueJ is closing, so the session is over and the files are about to be saved. */
SESSION_ENDED,

/** A project was opened. We only record this once per project per session. */
PROJECT_OPENED,

/** A project that was opened earlier in the session was closed. */
PROJECT_CLOSED,

/** A new class was added during this session (not one that was already in the project). */
CLASS_CREATED,

/** An existing class was edited, so BlueJ marked it as needing to be compiled again. */
CLASS_MODIFIED,

/** A class was renamed. The details keep both the old name and the new name. */
CLASS_RENAMED,

/** A class was deleted from the project. */
CLASS_REMOVED,

/** After compiling, we found a method the class didn't have before. */
METHOD_ADDED,

/** After compiling, a method the class used to have is gone. */
METHOD_REMOVED,

/**
 * Some text was typed or deleted in the editor. We use these to work out
 * typing speed and active vs idle time.
 */
DOCUMENT_EDIT,

/** BlueJ started compiling one or more files. */
COMPILE_STARTED,

/** The compiler found an error. One failed compile can give several of these. */
COMPILE_ERROR,

/** The compiler gave a warning. The compile can still work even with warnings. */
COMPILE_WARNING,

/** A compile finished with no errors. This counts as one compile attempt. */
COMPILE_SUCCEEDED,

/** A compile finished with at least one error. This counts as one compile attempt. */
COMPILE_FAILED,

// Execution

/** The student ran something, like {@code main}, another method or a constructor. */
PROGRAM_RUN,

/** A run crashed with an exception instead of finishing normally. */
RUNTIME_EXCEPTION,


    
    /** A short summary (number of lines and characters) of text that a run gave back. */
    CONSOLE_OUTPUT
}