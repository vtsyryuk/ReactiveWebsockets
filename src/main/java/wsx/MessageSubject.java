package wsx;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class MessageSubject {
    private final Map<String, String> fields;

    public MessageSubject(Map<String, String> fields) {
        this.fields = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(fields, "fields")));
    }

    public static MessageSubject of(String name, String value) {
        Map<String, String> fields = new HashMap<>(1);
        fields.put(name, value);
        return new MessageSubject(fields);
    }

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
