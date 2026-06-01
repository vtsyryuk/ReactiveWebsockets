package wsx;

/**
 * Immutable diagnostic event containing a severity level and human-readable text.
 */
public final class DiagnosticMessage {

    private final DiagnosticLevel level;
    private final String message;

    /**
     * Creates a diagnostic message.
     *
     * @param level diagnostic severity
     * @param message diagnostic text
     */
    public DiagnosticMessage(final DiagnosticLevel level, final String message) {
        this.level = level;
        this.message = message;
    }

    /**
     * Returns the diagnostic severity.
     *
     * @return diagnostic level
     */
    public DiagnosticLevel getLevel() {
        return level;
    }

    /**
     * Returns the diagnostic text.
     *
     * @return diagnostic message text
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the diagnostic severity for pair-style consumers.
     *
     * @return diagnostic level
     */
    public DiagnosticLevel getLeft() {
        return level;
    }

    /**
     * Returns the diagnostic text for pair-style consumers.
     *
     * @return diagnostic message text
     */
    public String getRight() {
        return message;
    }

    @Override
    public String toString() {
        return "DiagnosticMessage [level=" + level + ", message=" + message + "]";
    }
}
