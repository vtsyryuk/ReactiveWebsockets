package wsx;

import java.time.Instant;

public final class RequestMessage extends Message<RequestMessageType> {

    public static RequestMessage create(MessageSubject subject, RequestMessageType command) {
        return create(subject, command, Instant.now());
    }

    public static RequestMessage create(MessageSubject subject, RequestMessageType command, Instant timestamp) {
        RequestMessage msg = new RequestMessage();
        msg.setSubject(subject);
        msg.setContent(command);
        msg.setTimestamp(timestamp);
        return msg;
    }
}
