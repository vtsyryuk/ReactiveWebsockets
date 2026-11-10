package wsx;

import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import javax.websocket.CloseReason;
import javax.websocket.Session;

final class SpringEndpointWiringTest {

    @Test
    void replyOnlyContextCanStillCreateAClientEndpoint() {
        ReplyMessageHandler replyHandler = Mockito.mock(ReplyMessageHandler.class);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ReplyStreamSessionManager.class,
                    () -> new ReplyStreamSessionManager(remote -> replyHandler));
            context.getBeanFactory().registerSingleton("diagnosticPublisher",
                    PublishSubject.<DiagnosticMessage>create());
            context.register(SocketEndpoint.class);
            context.refresh();

            Session session = Mockito.mock(Session.class);
            Mockito.when(session.getId()).thenReturn("spring-client");
            SocketEndpoint endpoint = context.getBean(SocketEndpoint.class);
            endpoint.onOpen(session, null);

            Mockito.verify(session).addMessageHandler(replyHandler);
            endpoint.onClose(session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done"));
        }
    }

    @Test
    void serverEndpointUsesRequestManagerWhenBothStreamManagersAreRegistered() throws Exception {
        RequestMessageHandler requestHandler = Mockito.mock(RequestMessageHandler.class);
        ReplyMessageHandler replyHandler = Mockito.mock(ReplyMessageHandler.class);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(RequestStreamSessionManager.class,
                    () -> new RequestStreamSessionManager(remote -> requestHandler));
            context.registerBean(ReplyStreamSessionManager.class,
                    () -> new ReplyStreamSessionManager(remote -> replyHandler));
            context.getBeanFactory().registerSingleton("diagnosticPublisher",
                    PublishSubject.<DiagnosticMessage>create());
            context.register(SocketEndpoint.class);
            context.refresh();

            Session session = Mockito.mock(Session.class);
            Mockito.when(session.getId()).thenReturn("spring-server");
            SocketEndpoint endpoint = context.getBean(SocketEndpoint.class);
            endpoint.onOpen(session, null);

            Mockito.verify(session).addMessageHandler(requestHandler);
            Mockito.verify(session, Mockito.never()).addMessageHandler(replyHandler);

            endpoint.onClose(session, new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "done"));
            Mockito.verify(requestHandler).close();
        }
    }
}
