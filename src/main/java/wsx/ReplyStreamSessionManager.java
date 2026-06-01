package wsx;

import org.springframework.stereotype.Component;

/**
 * Session manager for websocket sessions that receive reply messages.
 */
@Component
public final class ReplyStreamSessionManager extends SimpleSessionManager<ReplyMessage> {

    /**
     * Creates a reply-stream session manager.
     *
     * @param messageHandlerFactory factory for reply handlers
     */
    public ReplyStreamSessionManager(MessageHandlerFactory<ReplyMessage> messageHandlerFactory) {
        super(messageHandlerFactory);
    }

}
