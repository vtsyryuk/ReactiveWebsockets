package wsx;

import org.javatuples.Pair;

import java.util.HashMap;
import java.util.Map;

public class MessageSubject {
    private final Map<String, String> fields;

    public MessageSubject(Map<String, String> fields) {
        this.fields = new HashMap<>(fields);
    }

    @SafeVarargs
    public MessageSubject(Pair<String, String>... fields) {
        this.fields = new HashMap<>(fields.length);
        for (Pair<String, String> item : fields) {
            this.fields.put(item.getValue0(), item.getValue1());
        }
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