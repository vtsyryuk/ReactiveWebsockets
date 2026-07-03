package wsx;

/**
 * Immutable diagnostic event containing a severity level and human-readable text.
 *
 * @param level diagnostic severity
 * @param message diagnostic text
 */
public record DiagnosticMessage(DiagnosticLevel level, String message) {

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

    @Override
    public String toString() {
        return "DiagnosticMessage [level=" + level + ", message=" + message + "]";
    }
}
