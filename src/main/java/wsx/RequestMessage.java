package wsx;

import org.joda.time.DateTime;

public final class RequestMessage extends Message<RequestMessageType> {

    public static RequestMessage create(MessageSubject subject, RequestMessageType command) {
        return create(subject, command, new DateTime());
    }

    public static RequestMessage create(MessageSubject subject, RequestMessageType command, DateTime timestamp) {
        RequestMessage msg = new RequestMessage();
        msg.setSubject(subject);
        msg.setContent(command);
        msg.setTimestamp(timestamp);
        return msg;
    }
}
