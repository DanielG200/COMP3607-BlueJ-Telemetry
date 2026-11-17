package tt.edu.uwi.comp3607.tracking;

/** How a program run or method call ended. Our own type, so tests need no BlueJ. */
public enum ExecutionOutcome {
    /** The call finished without error. */
    NORMAL,
    /** The call ended because an exception was thrown. */
    EXCEPTION,
    /** The call was stopped before it finished, for example by the user. */
    TERMINATED,
    /** BlueJ did not say how the call ended. */
    UNKNOWN
}
