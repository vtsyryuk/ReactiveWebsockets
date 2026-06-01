package wsx;

import javax.websocket.MessageHandler;
import java.io.Closeable;

/**
 * Websocket whole-message handler that also releases its resources when closed.
 *
 * @param <T> message type consumed by the handler
 */
public interface CloseableMessageHandler<T> extends MessageHandler.Whole<T>, Closeable {
}
