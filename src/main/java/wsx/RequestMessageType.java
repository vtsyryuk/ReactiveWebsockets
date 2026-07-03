package wsx;

/**
 * Commands that control a websocket subject subscription.
 */
public enum RequestMessageType {

    SUBSCRIBE("subscribe"),
    UNSUBSCRIBE("unsubscribe");

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
     * @return true when this is {@link #SUBSCRIBE}
     */
    public boolean isSubscribe() {
        return this == SUBSCRIBE;
    }

    /**
     * Reports whether this command closes a subscription.
     *
     * @return true when this is {@link #UNSUBSCRIBE}
     */
    public boolean isUnsubscribe() {
        return this == UNSUBSCRIBE;
    }

    @Override
    public String toString() {
        return getValue();
    }
}
