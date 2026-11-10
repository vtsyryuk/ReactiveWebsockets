package wsx;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

/**
 * Session manager for websocket sessions that receive request messages.
 * Preferred for Spring-managed server endpoints when both stream managers are registered.
 */
@Component
@Primary
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
