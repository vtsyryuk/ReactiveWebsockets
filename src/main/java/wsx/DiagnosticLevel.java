package wsx;

/**
 * Severity levels used for diagnostic messages emitted by websocket components.
 */
public enum DiagnosticLevel {

    FATAL(0),
    ERROR(3),
    WARN(4),
    INFO(6),
    DEBUG(7),
    TRACE(7);

    private final int value;

    private DiagnosticLevel(final int value) {
        this.value = value;
    }

    /**
     * Returns the numeric severity value associated with this level.
     *
     * @return severity value
     */
    public int getValue() {
        return value;
    }

    /**
     * Reports whether this level represents a fatal event.
     *
     * @return true when this level is fatal
     */
    public boolean isFatal() {
        return matchLevel(FATAL.value);
    }

    /**
     * Reports whether this level represents an error event.
     *
     * @return true when this level is error
     */
    public boolean isError() {
        return matchLevel(ERROR.value);
    }

    /**
     * Reports whether this level represents a warning event.
     *
     * @return true when this level is warning
     */
    public boolean isWarn() {
        return matchLevel(WARN.value);
    }

    /**
     * Reports whether this level represents an informational event.
     *
     * @return true when this level is informational
     */
    public boolean isInfo() {
        return matchLevel(INFO.value);
    }

    /**
     * Reports whether this level represents a debug event.
     *
     * @return true when this level is debug
     */
    public boolean isDebug() {
        return matchLevel(DEBUG.value);
    }

    /**
     * Reports whether this level represents a trace event.
     *
     * @return true when this level is trace
     */
    public boolean isTrace() {
        return matchLevel(TRACE.value);
    }

    private boolean matchLevel(int level) {
        return this.value == level;
    }

    @Override
    public String toString() {
        return this.name();
    }

}
