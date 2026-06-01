package wsx;

import io.reactivex.rxjava3.schedulers.TestScheduler;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    public void connectToServerReturnsWhenAlreadyClosed() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
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
        Method connectToServer = AutoReconnection.class.getDeclaredMethod("connectToServer");
        connectToServer.setAccessible(true);
        connectToServer.invoke(reconnection);
        scheduler.advanceTimeBy(5, TimeUnit.SECONDS);

        Mockito.verifyNoInteractions(container);
    }

    @Test
    public void connectsAfterDelayRunsGreetingAndClosesServerSession() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        Session serverSession = Mockito.mock(Session.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();
        boolean[] greeted = {false};

        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenReturn(serverSession);
        Mockito.when(serverSession.isOpen()).thenReturn(true);

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
                .greetingAction(() -> greeted[0] = true)
                .build();

        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        reconnection.close();

        assertTrue(greeted[0]);
        Mockito.verify(container).connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test"));
        Mockito.verify(serverSession).close();
    }

    @Test
    public void reconnectsAfterEndpointCloseAndPublishesConnectionStatus() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        SessionManager sessionManager = Mockito.mock(SessionManager.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        PublishSubject<ReplyMessage> replies = PublishSubject.create();
        List<ReplyMessage> replyMessages = new ArrayList<>();
        replies.subscribe(replyMessages::add);
        SocketEndpoint endpoint = new SocketEndpoint(sessionManager, diagnostics);
        TestScheduler scheduler = new TestScheduler();
        Session closedSession = Mockito.mock(Session.class);
        CloseReason closeReason = new CloseReason(CloseCodes.NORMAL_CLOSURE, "Goodbye");

        Mockito.when(closedSession.getId()).thenReturn("1");

        AutoReconnection reconnection = new AutoReconnection.Builder(
                container,
                endpoint,
                clientConfig,
                URI.create("ws://localhost/test"),
                replies)
                .diagnosticPublisher(diagnostics)
                .scheduler(scheduler)
                .delay(1)
                .period(1)
                .timeUnit(TimeUnit.SECONDS)
                .build();

        endpoint.onClose(closedSession, closeReason);
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        reconnection.close();

        assertTrue(replyMessages.stream().anyMatch(message -> "Goodbye".equals(message.getContent())));
        Mockito.verify(container).connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test"));
    }

    @Test
    public void connectionFailuresArePublishedAsDiagnostics() {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        List<DiagnosticMessage> messages = new ArrayList<>();
        diagnostics.subscribe(messages::add);
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();

        try {
            Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                    .thenThrow(new IOException("cannot connect"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        new AutoReconnection.Builder(
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

        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);

        assertTrue(messages.stream().anyMatch(message -> message.getLevel() == DiagnosticLevel.ERROR
                && message.getMessage().contains("cannot connect")));
    }

    @Test
    public void closePublishesDiagnosticsWhenServerSessionCloseFails() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        Session serverSession = Mockito.mock(Session.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        List<DiagnosticMessage> messages = new ArrayList<>();
        diagnostics.subscribe(messages::add);
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();

        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenReturn(serverSession);
        Mockito.when(serverSession.isOpen()).thenReturn(true);
        Mockito.doThrow(new IOException("close failed")).when(serverSession).close();

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

        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        reconnection.close();

        assertTrue(messages.stream().anyMatch(message -> message.getLevel() == DiagnosticLevel.ERROR
                && message.getMessage().contains("close failed")));
    }

    @Test
    public void builderRejectsInvalidArguments() {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), PublishSubject.create());
        AutoReconnection.Builder builder = new AutoReconnection.Builder(
                container,
                endpoint,
                clientConfig,
                URI.create("ws://localhost/test"),
                PublishSubject.<ReplyMessage>create());

        assertThrows(IllegalArgumentException.class, () -> builder.delay(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.period(0));
        assertThrows(NullPointerException.class, () -> builder.scheduler(null));
        assertThrows(NullPointerException.class, () -> builder.timeUnit(null));
        assertThrows(NullPointerException.class, () -> builder.diagnosticPublisher(null));
        builder.greetingAction(null);
    }
}
