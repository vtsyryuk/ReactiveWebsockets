package wsx;

import org.springframework.stereotype.Component;

@Component
public final class RequestStreamSessionManager extends SimpleSessionManager<RequestMessage> {

    public RequestStreamSessionManager(MessageHandlerFactory<RequestMessage> messageHandlerFactory) {
        super(messageHandlerFactory);
    }

}
