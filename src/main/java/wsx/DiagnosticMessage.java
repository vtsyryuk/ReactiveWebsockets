package wsx;

public final class DiagnosticMessage {

    private final DiagnosticLevel level;
    private final String message;

    public DiagnosticMessage(final DiagnosticLevel level, final String message) {
        this.level = level;
        this.message = message;
    }

    public DiagnosticLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public DiagnosticLevel getLeft() {
        return level;
    }

    public String getRight() {
        return message;
    }

    @Override
    public String toString() {
        return "DiagnosticMessage [level=" + level + ", message=" + message + "]";
    }
}
