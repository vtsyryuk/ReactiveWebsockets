package wsx;

import org.joda.time.DateTime;


public final class ReplyMessage extends Message<String> {

    public static ReplyMessage create(MessageSubject subject, String content) {
        return create(subject, content, new DateTime());
    }

    public static ReplyMessage create(MessageSubject subject, String content, DateTime timestamp) {
        ReplyMessage msg = new ReplyMessage();
        msg.setSubject(subject);
        msg.setContent(content);
        msg.setTimestamp(timestamp);
        return msg;
    }
}