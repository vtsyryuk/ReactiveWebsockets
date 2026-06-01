package wsx;

import java.time.Instant;

public class Message<T> {

    private T content;
    private MessageSubject subject;
    private Instant timestamp;

    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
    }

    public MessageSubject getSubject() {
        return subject;
    }

    public void setSubject(MessageSubject subject) {
        this.subject = subject;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Message [timestamp=" + timestamp + ", content=" + content + ", subject=" + subject + "]";
    }
}
