package wsx;

import org.springframework.stereotype.Component;

/**
 * Websocket JSON decoder for {@link ReplyMessage} instances.
 */
@Component
public final class ReplyMessageDecoder extends MessageDecoder<ReplyMessage> {

    /**
     * Returns the concrete reply message class used by Gson.
     *
     * @return reply message class
     */
    @Override
    protected Class<ReplyMessage> messageClass() {
        return ReplyMessage.class;
    }
}
