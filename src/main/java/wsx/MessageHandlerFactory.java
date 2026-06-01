package wsx;

import javax.websocket.RemoteEndpoint.Async;

/**
 * Creates websocket message handlers bound to a remote endpoint.
 *
 * @param <T> message type handled by the created handler
 */
public interface MessageHandlerFactory<T> {

    /**
     * Creates a closeable whole-message handler for the supplied remote endpoint.
     *
     * @param remoteEndpoint endpoint used for outbound websocket sends
     * @return configured message handler
     */
    CloseableMessageHandler<T> create(Async remoteEndpoint);
}
