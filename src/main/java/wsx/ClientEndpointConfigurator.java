package wsx;

import org.springframework.stereotype.Component;

import javax.websocket.ClientEndpointConfig;
/**
 * Client endpoint configurator hook for websocket handshake customization.
 */
@Component
public final class ClientEndpointConfigurator extends ClientEndpointConfig.Configurator {
}
