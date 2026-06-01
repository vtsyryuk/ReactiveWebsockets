package wsx;

import org.springframework.stereotype.Component;

/**
 * Websocket JSON decoder for {@link RequestMessage} instances.
 */
@Component
public final class RequestMessageDecoder extends MessageDecoder<RequestMessage> {

    /**
     * Returns the concrete request message class used by Gson.
     *
     * @return request message class
     */
    @Override
    protected Class<RequestMessage> messageClass() {
        return RequestMessage.class;
    }
}
