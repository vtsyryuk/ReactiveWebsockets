package wsx;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable set of key/value fields used to route request and reply messages.
 */
public final class MessageSubject {
    private final Map<String, String> fields;

    /**
     * Creates a subject from the supplied fields.
     *
     * @param fields subject fields; copied defensively
     */
    public MessageSubject(Map<String, String> fields) {
        this.fields = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(fields, "fields")));
    }

    /**
     * Creates a subject containing a single field.
     *
     * @param name field name
     * @param value field value
     * @return immutable message subject
     */
    public static MessageSubject of(String name, String value) {
        Map<String, String> fields = new HashMap<>(1);
        fields.put(name, value);
        return new MessageSubject(fields);
    }

    /**
     * Returns the immutable subject fields.
     *
     * @return subject fields
     */
    public Map<String, String> getFields() {
        return fields;
    }

    @Override
    public boolean equals(Object obj) {

        return obj instanceof MessageSubject && (obj == this || this.fields.equals(((MessageSubject) obj).fields));
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    @Override
    public String toString() {
        return fields.toString();
    }
}
