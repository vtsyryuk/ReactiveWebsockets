package wsx;

import org.springframework.stereotype.Component;

/**
 * Websocket JSON encoder for {@link RequestMessage} instances.
 */
@Component
public final class RequestMessageEncoder extends MessageEncoder<RequestMessage> {
}
