package wsx;

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

    public int getValue() {
        return value;
    }

    public boolean isFatal() {
        return matchLevel(FATAL.value);
    }

    public boolean isError() {
        return matchLevel(ERROR.value);
    }

    public boolean isWarn() {
        return matchLevel(WARN.value);
    }

    public boolean isInfo() {
        return matchLevel(INFO.value);
    }

    public boolean isDebug() {
        return matchLevel(DEBUG.value);
    }

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