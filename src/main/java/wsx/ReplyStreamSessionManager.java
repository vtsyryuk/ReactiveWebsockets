package wsx;

import org.springframework.stereotype.Component;

@Component
public final class ReplyStreamSessionManager extends SimpleSessionManager<ReplyMessage> {

    public ReplyStreamSessionManager(MessageHandlerFactory<ReplyMessage> messageHandlerFactory) {
        super(messageHandlerFactory);
    }

}
