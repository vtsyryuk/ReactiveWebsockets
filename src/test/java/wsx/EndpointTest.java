package wsx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.disposables.Disposable;

import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.EndpointConfig;
import javax.websocket.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class EndpointTest {

    private List<DiagnosticMessage> diagnosticMessages;
    private SessionManager manager;
    private SocketEndpoint endpoint;

    @BeforeEach
    public void setUp() {
        diagnosticMessages = new ArrayList<>();
        manager = Mockito.mock(SessionManager.class);
        PublishSubject<DiagnosticMessage> diagnosticService = PublishSubject.create();
        diagnosticService.subscribe(diagnosticMessages::add);
        endpoint = new SocketEndpoint(manager, diagnosticService);
    }

    @AfterEach
    public void teardown() {
    }

    @Test
    public void onOpenTest() throws IOException {
        try (Session session1 = Mockito.mock(Session.class)) {
            Mockito.when(session1.getId()).thenReturn("1");
            Mockito.when(manager.attach(session1)).thenReturn(Disposable.empty());

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
            Mockito.when(manager.attach(session1)).thenReturn(Disposable.empty());

            endpoint.onError(session1, new Exception("TestError1"));
            assertEquals(1, diagnosticMessages.size());
        }
    }

    @Test
    public void onCloseTest() throws IOException {
        try (Session session1 = Mockito.mock(Session.class)) {
            Mockito.when(session1.getId()).thenReturn("1");
            final boolean[] sessionClosed = {false};

            Mockito.when(manager.attach(session1)).thenReturn(Disposable.fromAction(() -> sessionClosed[0] = true));
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
