package wsx;

/**
 * Commands that control a websocket subject subscription.
 */
public enum RequestMessageType {

    Subscribe("subscribe"),
    Unsubscribe("unsubscribe");

    private final String value;

    private RequestMessageType(final String value) {
        this.value = value;
    }

    /**
     * Returns the wire value used in serialized request messages.
     *
     * @return command wire value
     */
    public String getValue() {
        return value;
    }

    /**
     * Reports whether this command opens a subscription.
     *
     * @return true when this is {@link #Subscribe}
     */
    public boolean isSubscribe() {
        return this.value.equals(Subscribe.value);
    }

    /**
     * Reports whether this command closes a subscription.
     *
     * @return true when this is {@link #Unsubscribe}
     */
    public boolean isUnsubscribe() {
        return this.value.equals(Unsubscribe.value);
    }

    @Override
    public String toString() {
        return getValue();
    }
}
