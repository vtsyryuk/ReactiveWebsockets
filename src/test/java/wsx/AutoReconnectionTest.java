package wsx;

import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.WebSocketContainer;
import java.net.URI;
import java.util.concurrent.TimeUnit;

public final class AutoReconnectionTest {

    @Test
    public void closeCancelsPendingReconnectAttempt() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        SessionManager sessionManager = Mockito.mock(SessionManager.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(sessionManager, diagnostics);
        TestScheduler scheduler = new TestScheduler();

        AutoReconnection reconnection = new AutoReconnection.Builder(
                container,
                endpoint,
                clientConfig,
                URI.create("ws://localhost/test"),
                PublishSubject.<ReplyMessage>create())
                .diagnosticPublisher(diagnostics)
                .scheduler(scheduler)
                .delay(1)
                .period(1)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        reconnection.close();
        scheduler.advanceTimeBy(5, TimeUnit.SECONDS);

        Mockito.verifyNoInteractions(container);
    }
}
