package wsx;

import java.time.Instant;


public final class ReplyMessage extends Message<String> {

    public static ReplyMessage create(MessageSubject subject, String content) {
        return create(subject, content, Instant.now());
    }

    public static ReplyMessage create(MessageSubject subject, String content, Instant timestamp) {
        ReplyMessage msg = new ReplyMessage();
        msg.setSubject(subject);
        msg.setContent(content);
        msg.setTimestamp(timestamp);
        return msg;
    }
}
