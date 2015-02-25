package wsx;

import org.joda.time.DateTime;

public class Message<T> {

    private T content;
    private MessageSubject subject;
    private DateTime timestamp;

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

    public DateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "Message [timestamp=" + timestamp + ", content=" + content + ", subject=" + subject + "]";
    }
}
