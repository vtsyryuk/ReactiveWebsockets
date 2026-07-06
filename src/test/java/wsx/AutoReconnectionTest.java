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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AutoReconnectionTest {

    @Test
    void closeCancelsPendingReconnectAttempt() throws Exception {
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
    void endpointCloseDoesNotScheduleReconnectAfterAutoReconnectionIsClosed() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();
        Session closedSession = Mockito.mock(Session.class);
        CloseReason closeReason = new CloseReason(CloseCodes.NORMAL_CLOSURE, "Goodbye");

        Mockito.when(closedSession.getId()).thenReturn("1");

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
        endpoint.onClose(closedSession, closeReason);
        scheduler.advanceTimeBy(5, TimeUnit.SECONDS);

        Mockito.verifyNoInteractions(container);
    }

    @Test
    void connectsAfterDelayRunsGreetingAndClosesServerSession() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        Session connectedSession = Mockito.mock(Session.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();
        boolean[] greeted = {false};

        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenReturn(connectedSession);
        Mockito.when(connectedSession.isOpen()).thenReturn(true);

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
        Mockito.verify(connectedSession).close();
    }

    @Test
    void reconnectsAfterEndpointCloseAndPublishesConnectionStatus() throws Exception {
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
    void reconnectClosesPreviousActiveSessionWhenReplacementConnects() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        SessionManager sessionManager = Mockito.mock(SessionManager.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(sessionManager, diagnostics);
        TestScheduler scheduler = new TestScheduler();
        Session firstConnectedSession = Mockito.mock(Session.class);
        Session secondConnectedSession = Mockito.mock(Session.class);
        Session closedEndpointSession = Mockito.mock(Session.class);
        CloseReason closeReason = new CloseReason(CloseCodes.NORMAL_CLOSURE, "Reconnect");

        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenReturn(firstConnectedSession, secondConnectedSession);
        Mockito.when(firstConnectedSession.isOpen()).thenReturn(true);
        Mockito.when(secondConnectedSession.isOpen()).thenReturn(true);
        Mockito.when(closedEndpointSession.getId()).thenReturn("1");

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
        endpoint.onClose(closedEndpointSession, closeReason);
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        reconnection.close();

        Mockito.verify(firstConnectedSession).close();
        Mockito.verify(secondConnectedSession).close();
    }

    @Test
    void reconnectKeepsSameActiveSessionWhenReplacementIsSameInstance() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        SessionManager sessionManager = Mockito.mock(SessionManager.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(sessionManager, diagnostics);
        TestScheduler scheduler = new TestScheduler();
        Session connectedSession = Mockito.mock(Session.class);
        Session closedEndpointSession = Mockito.mock(Session.class);
        CloseReason closeReason = new CloseReason(CloseCodes.NORMAL_CLOSURE, "Reconnect");

        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenReturn(connectedSession, connectedSession);
        Mockito.when(connectedSession.isOpen()).thenReturn(true);
        Mockito.when(closedEndpointSession.getId()).thenReturn("1");

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
        endpoint.onClose(closedEndpointSession, closeReason);
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        reconnection.close();

        Mockito.verify(connectedSession).close();
    }

    @Test
    void reconnectDoesNotClosePreviousActiveSessionWhenItIsAlreadyClosed() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        SessionManager sessionManager = Mockito.mock(SessionManager.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(sessionManager, diagnostics);
        TestScheduler scheduler = new TestScheduler();
        Session firstConnectedSession = Mockito.mock(Session.class);
        Session secondConnectedSession = Mockito.mock(Session.class);
        Session closedEndpointSession = Mockito.mock(Session.class);
        CloseReason closeReason = new CloseReason(CloseCodes.NORMAL_CLOSURE, "Reconnect");

        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenReturn(firstConnectedSession, secondConnectedSession);
        Mockito.when(firstConnectedSession.isOpen()).thenReturn(false);
        Mockito.when(secondConnectedSession.isOpen()).thenReturn(true);
        Mockito.when(closedEndpointSession.getId()).thenReturn("1");

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
        endpoint.onClose(closedEndpointSession, closeReason);
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        reconnection.close();

        Mockito.verify(firstConnectedSession, Mockito.never()).close();
        Mockito.verify(secondConnectedSession).close();
    }

    @Test
    void connectionFailuresArePublishedAsDiagnostics() {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        List<DiagnosticMessage> messages = new ArrayList<>();
        diagnostics.subscribe(messages::add);
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();

        assertDoesNotThrow(() ->
            Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                    .thenThrow(new IOException("cannot connect")));

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
    void closePublishesDiagnosticsWhenServerSessionCloseFails() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        Session connectedSession = Mockito.mock(Session.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        List<DiagnosticMessage> messages = new ArrayList<>();
        diagnostics.subscribe(messages::add);
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();

        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenReturn(connectedSession);
        Mockito.when(connectedSession.isOpen()).thenReturn(true);
        Mockito.doThrow(new IOException("close failed")).when(connectedSession).close();

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
    void closeDoesNotCloseAlreadyClosedConnectedSession() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        Session connectedSession = Mockito.mock(Session.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();

        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenReturn(connectedSession);
        Mockito.when(connectedSession.isOpen()).thenReturn(false);

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

        Mockito.verify(connectedSession, Mockito.never()).close();
    }

    @Test
    void closeIsIdempotentAfterActiveSessionIsClosed() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        Session connectedSession = Mockito.mock(Session.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();

        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenReturn(connectedSession);
        Mockito.when(connectedSession.isOpen()).thenReturn(true);

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
        reconnection.close();

        Mockito.verify(connectedSession).close();
    }

    @Test
    void closeDuringConnectionAttemptClosesLateServerSession() throws Exception {
        WebSocketContainer container = Mockito.mock(WebSocketContainer.class);
        ClientEndpointConfig clientConfig = Mockito.mock(ClientEndpointConfig.class);
        Session lateConnectedSession = Mockito.mock(Session.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        SocketEndpoint endpoint = new SocketEndpoint(Mockito.mock(SessionManager.class), diagnostics);
        TestScheduler scheduler = new TestScheduler();
        CountDownLatch connectStarted = new CountDownLatch(1);
        CountDownLatch releaseConnection = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Mockito.when(lateConnectedSession.isOpen()).thenReturn(true);
        Mockito.when(container.connectToServer(endpoint, clientConfig, URI.create("ws://localhost/test")))
                .thenAnswer(invocation -> {
                    connectStarted.countDown();
                    assertTrue(releaseConnection.await(5, TimeUnit.SECONDS));
                    return lateConnectedSession;
                });

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

        try {
            Future<?> connectionTick = executor.submit(() -> scheduler.advanceTimeBy(1, TimeUnit.SECONDS));
            assertTrue(connectStarted.await(5, TimeUnit.SECONDS));

            reconnection.close();
            releaseConnection.countDown();
            connectionTick.get(5, TimeUnit.SECONDS);

            Mockito.verify(lateConnectedSession).close();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void builderRejectsInvalidArguments() {
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
