package wsx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

/**
 * Server endpoint configurator that exposes Spring-managed endpoint instances to the websocket runtime.
 */
@Component
public final class ServerEndpointConfigurator extends ServerEndpointConfig.Configurator {

    private final SocketEndpoint socketEndpoint;

    /**
     * Creates a server configurator for the supplied Spring-managed socket endpoint.
     *
     * @param socketEndpoint endpoint instance to return to the websocket runtime
     */
    @Autowired
    public ServerEndpointConfigurator(SocketEndpoint socketEndpoint) {
        this.socketEndpoint = socketEndpoint;
    }

    /**
     * Copies the HTTP session into websocket user properties when one exists.
     *
     * @param sec server endpoint configuration
     * @param request handshake request
     * @param response handshake response
     */
    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        if (request.getHttpSession() instanceof HttpSession httpSession) {
            sec.getUserProperties().put(HttpSession.class.getName(), httpSession);
        }
    }

    /**
     * Returns the Spring-managed {@link SocketEndpoint} instance for endpoint creation.
     *
     * @param endpointClass endpoint type requested by the websocket runtime
     * @param <T> endpoint type
     * @return managed endpoint instance when requested, otherwise the default instance
     * @throws InstantiationException if the default configurator cannot create the endpoint
     */
    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        if (endpointClass.equals(SocketEndpoint.class)) {
            return endpointClass.cast(socketEndpoint);
        }
        return super.getEndpointInstance(endpointClass);
    }
}
