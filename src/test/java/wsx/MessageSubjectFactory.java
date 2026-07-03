package wsx;

final class MessageSubjectFactory {

    public static MessageSubject create(String name, String value) {
        return MessageSubject.of(name, value);
    }
}
