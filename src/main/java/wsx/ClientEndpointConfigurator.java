package wsx;

import org.springframework.stereotype.Component;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.HandshakeResponse;
import java.util.List;
import java.util.Map;

@Component
public final class ClientEndpointConfigurator extends ClientEndpointConfig.Configurator {

    /**
     * This method is called by the implementation after it has formulated the handshake
     * request that will be used to initiate the connection to the server, but before it has
     * sent any part of the request. This allows the developer to inspect and modify the
     * handshake request headers prior to the start of the handshake interaction.
     *
     * @param headers the mutable map of handshake request headers the implementation is about to send to
     *                start the handshake interaction.
     */
    public void beforeRequest(Map<String, List<String>> headers) {
        super.beforeRequest(headers);
    }

    /**
     * This method is called by the implementation after it has received a handshake response
     * from the server as a result of a handshake interaction it initiated. The developer may implement
     * this method in order to inspect the returning handshake response.
     *
     * @param hr the handshake response sent by the server.
     */
    public void afterResponse(HandshakeResponse hr) {
        super.afterResponse(hr);
    }
}