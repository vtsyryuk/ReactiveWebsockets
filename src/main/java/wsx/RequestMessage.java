package wsx;

import java.time.Instant;

/**
 * Subscription control message sent by a websocket client.
 */
public final class RequestMessage extends Message<RequestMessageType> {

    /**
     * Creates a request message with the current timestamp.
     *
     * @param subject subject to subscribe or unsubscribe
     * @param command request command
     * @return initialized request message
     */
    public static RequestMessage create(MessageSubject subject, RequestMessageType command) {
        return create(subject, command, Instant.now());
    }

    /**
     * Creates a request message with an explicit timestamp.
     *
     * @param subject subject to subscribe or unsubscribe
     * @param command request command
     * @param timestamp timestamp to store in the message
     * @return initialized request message
     */
    public static RequestMessage create(MessageSubject subject, RequestMessageType command, Instant timestamp) {
        RequestMessage msg = new RequestMessage();
        msg.setSubject(subject);
        msg.setContent(command);
        msg.setTimestamp(timestamp);
        return msg;
    }
}
