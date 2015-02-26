package wsx;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.PublishSubject;
import rx.subscriptions.Subscriptions;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class EndpointTest {

    private List<DiagnosticMessage> diagnosticMessages;
    private SessionManager manager;
    private SocketEndpoint endpoint;

    @Before
    public void setUp() {
        diagnosticMessages = new ArrayList<>();
        manager = Mockito.mock(SessionManager.class);
        PublishSubject<DiagnosticMessage> diagnosticService = PublishSubject.create();
        diagnosticService.subscribe(new Action1<DiagnosticMessage>() {
            @Override
            public void call(DiagnosticMessage t1) {
                diagnosticMessages.add(t1);
            }
        });
        endpoint = new SocketEndpoint(manager, diagnosticService);
    }

    @After
    public void teardown() {
    }

    @Test
    public void onOpenTest() throws IOException {
        try (Session session1 = Mockito.mock(Session.class)) {
            Mockito.when(session1.getId()).thenReturn("1");
            Mockito.when(manager.attach(session1)).thenReturn(Subscriptions.empty());

            try (Session session2 = Mockito.mock(Session.class)) {
                EndpointConfig config = Mockito.mock(EndpointConfig.class);
                endpoint.onOpen(session1, config);

                Mockito.verify(manager, Mockito.times(1)).attach(session1);
                Mockito.verify(manager, Mockito.never()).attach(session2);
            }
        }
    }

    @Test
    public void onErrorTest() throws IOException {
        try (Session session1 = Mockito.mock(Session.class)) {
            Mockito.when(session1.getId()).thenReturn("1");
            Mockito.when(manager.attach(session1)).thenReturn(Subscriptions.empty());

            endpoint.onError(session1, new Exception("TestError1"));
            assertEquals(1, diagnosticMessages.size());
        }
    }

    @Test
    public void onCloseTest() throws IOException {
        try (Session session1 = Mockito.mock(Session.class)) {
            Mockito.when(session1.getId()).thenReturn("1");
            final boolean[] sessionClosed = {false};

            final Action0 unsubscribe = new Action0() {

                @Override
                public void call() {
                    // TODO Auto-generated method stub
                    sessionClosed[0] = true;
                }
            };
            Mockito.when(manager.attach(session1)).thenReturn(Subscriptions.create(unsubscribe));
            CloseReason closeReason = new CloseReason(CloseCodes.NORMAL_CLOSURE, "Goodbye");
            endpoint.onClose(session1, closeReason);

            // we haven't opened the session yet
            assertFalse(sessionClosed[0]);

            EndpointConfig config = Mockito.mock(EndpointConfig.class);
            endpoint.onOpen(session1, config);
            endpoint.onClose(session1, closeReason);

            assertTrue(sessionClosed[0]);
        }
    }
}
