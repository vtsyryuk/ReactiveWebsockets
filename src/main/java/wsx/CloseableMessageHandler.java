package wsx;

import javax.websocket.MessageHandler;
import java.io.Closeable;

public interface CloseableMessageHandler<T> extends MessageHandler.Whole<T>, Closeable {
}