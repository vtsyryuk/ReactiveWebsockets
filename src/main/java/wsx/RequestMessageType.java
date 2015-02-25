package wsx;

public enum RequestMessageType {

    Subscribe("subscribe"),
    Unsubscribe("unsubscribe");

    private final String value;

    private RequestMessageType(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isSubscribe() {
        return this.value.equals(Subscribe.value);
    }

    public boolean isUnsubscribe() {
        return this.value.equals(Unsubscribe.value);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
