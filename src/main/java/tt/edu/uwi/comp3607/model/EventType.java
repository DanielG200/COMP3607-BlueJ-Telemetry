 package tt.edu.uwi.comp3607.model;

/**
 * Every kind of action the plugin can record.
 */
public enum EventType {

    // Session
    SESSION_STARTED,
    SESSION_ENDED,

    // Project and class lifecycle
    PROJECT_OPENED,
    PROJECT_CLOSED,
    CLASS_CREATED,
    CLASS_MODIFIED,
    CLASS_RENAMED,
    CLASS_REMOVED,
    METHOD_ADDED,
    METHOD_REMOVED,

    // Editor
    DOCUMENT_EDIT,

    // Compilation
    COMPILE_STARTED,
    COMPILE_ERROR,
    COMPILE_WARNING,
    COMPILE_SUCCEEDED,
    COMPILE_FAILED,

    // Execution
    PROGRAM_RUN,
    RUNTIME_EXCEPTION,
    CONSOLE_OUTPUT
}