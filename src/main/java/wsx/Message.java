package wsx;

import java.time.Instant;

/**
 * Base websocket message carrying a typed payload, routing subject, and timestamp.
 *
 * @param <T> payload type stored in the message content
 */
public class Message<T> {

    private T content;
    private MessageSubject subject;
    private Instant timestamp;

    /**
     * Returns the typed payload carried by this message.
     *
     * @return message content
     */
    public T getContent() {
        return content;
    }

    /**
     * Sets the typed payload carried by this message.
     *
     * @param content message content
     */
    public void setContent(T content) {
        this.content = content;
    }

    /**
     * Returns the routing subject for this message.
     *
     * @return message subject
     */
    public MessageSubject getSubject() {
        return subject;
    }

    /**
     * Sets the routing subject for this message.
     *
     * @param subject message subject
     */
    public void setSubject(MessageSubject subject) {
        this.subject = subject;
    }

    /**
     * Returns the time associated with this message.
     *
     * @return message timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Sets the time associated with this message.
     *
     * @param timestamp message timestamp
     */
    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Message [timestamp=" + timestamp + ", content=" + content + ", subject=" + subject + "]";
    }
}
