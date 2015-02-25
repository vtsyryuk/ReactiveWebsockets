package wsx;

import org.apache.commons.lang3.tuple.Pair;

public final class DiagnosticMessage extends Pair<DiagnosticLevel, String> {

    private static final long serialVersionUID = 39629977985014918L;

    private final DiagnosticLevel level;
    private final String message;

    public DiagnosticMessage(final DiagnosticLevel level, final String message) {
        this.level = level;
        this.message = message;
    }

    @Override
    public DiagnosticLevel getLeft() {
        return level;
    }

    @Override
    public String getRight() {
        return message;
    }

    @Override
    public String setValue(String value) {
        return null;
    }
}