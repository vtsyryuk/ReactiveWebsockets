package wsx;

import java.time.Instant;

/**
 * Data or status message sent from a websocket endpoint back to a subscriber.
 */
public final class ReplyMessage extends Message<String> {

    /**
     * Creates a reply message with the current timestamp.
     *
     * @param subject subject the reply belongs to
     * @param content reply payload
     * @return initialized reply message
     */
    public static ReplyMessage create(MessageSubject subject, String content) {
        return create(subject, content, Instant.now());
    }

    /**
     * Creates a reply message with an explicit timestamp.
     *
     * @param subject subject the reply belongs to
     * @param content reply payload
     * @param timestamp timestamp to store in the message
     * @return initialized reply message
     */
    public static ReplyMessage create(MessageSubject subject, String content, Instant timestamp) {
        ReplyMessage msg = new ReplyMessage();
        msg.setSubject(subject);
        msg.setContent(content);
        msg.setTimestamp(timestamp);
        return msg;
    }
}
