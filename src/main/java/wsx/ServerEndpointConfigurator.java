package wsx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpSession;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

@Component
public final class ServerEndpointConfigurator extends ServerEndpointConfig.Configurator {

    private final SocketEndpoint socketEndpoint;

    @Autowired
    public ServerEndpointConfigurator(SocketEndpoint socketEndpoint) {
        this.socketEndpoint = socketEndpoint;
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        HttpSession httpSession = (HttpSession) request.getHttpSession();
        if (httpSession != null) {
            sec.getUserProperties().put(HttpSession.class.getName(), httpSession);
        }
    }

    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
        if (endpointClass.equals(SocketEndpoint.class)) {
            return ((T) socketEndpoint);
        }
        return super.getEndpointInstance(endpointClass);
    }
}