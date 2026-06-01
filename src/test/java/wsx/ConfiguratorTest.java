package wsx;

import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpSession;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ConfiguratorTest {

    @Test
    public void serverConfiguratorStoresHttpSessionDuringHandshake() {
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), PublishSubject.create());
        ServerEndpointConfigurator configurator = new ServerEndpointConfigurator(endpoint);
        ServerEndpointConfig config = Mockito.mock(ServerEndpointConfig.class);
        HandshakeRequest request = Mockito.mock(HandshakeRequest.class);
        HandshakeResponse response = Mockito.mock(HandshakeResponse.class);
        HttpSession httpSession = Mockito.mock(HttpSession.class);
        Map<String, Object> userProperties = new HashMap<>();

        Mockito.when(config.getUserProperties()).thenReturn(userProperties);
        Mockito.when(request.getHttpSession()).thenReturn(httpSession);

        configurator.modifyHandshake(config, request, response);

        assertSame(httpSession, userProperties.get(HttpSession.class.getName()));
    }

    @Test
    public void serverConfiguratorReturnsInjectedSocketEndpoint() throws InstantiationException {
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), PublishSubject.create());
        ServerEndpointConfigurator configurator = new ServerEndpointConfigurator(endpoint);

        assertSame(endpoint, configurator.getEndpointInstance(SocketEndpoint.class));
    }

    @Test
    public void serverConfiguratorIgnoresMissingHttpSession() {
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), PublishSubject.create());
        ServerEndpointConfigurator configurator = new ServerEndpointConfigurator(endpoint);
        ServerEndpointConfig config = Mockito.mock(ServerEndpointConfig.class);
        HandshakeRequest request = Mockito.mock(HandshakeRequest.class);
        Map<String, Object> userProperties = new HashMap<>();

        Mockito.when(config.getUserProperties()).thenReturn(userProperties);
        Mockito.when(request.getHttpSession()).thenReturn(null);

        configurator.modifyHandshake(config, request, Mockito.mock(HandshakeResponse.class));

        assertTrue(userProperties.isEmpty());
    }

    @Test
    public void serverConfiguratorDelegatesOtherEndpointTypes() throws InstantiationException {
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), PublishSubject.create());
        ServerEndpointConfigurator configurator = new ServerEndpointConfigurator(endpoint);

        assertThrows(RuntimeException.class, () -> configurator.getEndpointInstance(DefaultEndpoint.class));
    }

    @Test
    public void clientConfiguratorAcceptsDefaultHandshakeHooks() {
        ClientEndpointConfigurator configurator = new ClientEndpointConfigurator();

        configurator.beforeRequest(new HashMap<>());
        configurator.afterResponse(Mockito.mock(HandshakeResponse.class));

        assertTrue(true);
    }

    public static final class DefaultEndpoint extends Endpoint {
        @Override
        public void onOpen(Session session, EndpointConfig config) {
        }
    }
}
