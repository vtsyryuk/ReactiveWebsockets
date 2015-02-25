package wsx;

import java.util.HashMap;
import java.util.Map;

public final class MessageSubjectFactory {

    public static MessageSubject create(String name, String value) {
        Map<String, String> m = new HashMap<>(1);
        m.put(name, value);
        return new MessageSubject(m);
    }
}