package wsx;

import org.springframework.stereotype.Component;

/**
 * Session manager for websocket sessions that receive request messages.
 */
@Component
public final class RequestStreamSessionManager extends SimpleSessionManager<RequestMessage> {

    /**
     * Creates a request-stream session manager.
     *
     * @param messageHandlerFactory factory for request handlers
     */
    public RequestStreamSessionManager(MessageHandlerFactory<RequestMessage> messageHandlerFactory) {
        super(messageHandlerFactory);
    }

}
