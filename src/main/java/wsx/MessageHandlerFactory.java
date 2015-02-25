package wsx;

import javax.websocket.RemoteEndpoint.Async;

public interface MessageHandlerFactory<T> {
    CloseableMessageHandler<T> create(Async remoteEndpoint);
}