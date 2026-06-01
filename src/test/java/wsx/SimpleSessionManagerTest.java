package wsx;

import io.reactivex.rxjava3.disposables.Disposable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.Session;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class SimpleSessionManagerTest {

    @Test
    public void constructorRejectsNullFactory() {
        assertThrows(NullPointerException.class, () -> new SimpleSessionManager<>(null));
    }

    @Test
    public void disposeClosesHandlerAndRemovesWrappedHandler() {
        Session session = Mockito.mock(Session.class);
        Async remote = Mockito.mock(Async.class);
        TestMessageHandler handler = new TestMessageHandler();
        WrappedHandler wrappedHandler = new WrappedHandler(handler);
        SimpleSessionManager<String> manager = new SimpleSessionManager<>(endpoint -> handler);

        Mockito.when(session.getAsyncRemote()).thenReturn(remote);
        Mockito.when(session.getMessageHandlers()).thenReturn(Set.of(wrappedHandler));

        Disposable disposable = manager.attach(session);
        disposable.dispose();

        assertTrue(handler.closed);
        Mockito.verify(session).addMessageHandler(handler);
        Mockito.verify(session).removeMessageHandler(wrappedHandler);
    }

    @Test
    public void disposeFallsBackToOriginalHandlerWhenNoWrapperExists() {
        Session session = Mockito.mock(Session.class);
        Async remote = Mockito.mock(Async.class);
        TestMessageHandler handler = new TestMessageHandler();
        SimpleSessionManager<String> manager = new SimpleSessionManager<>(endpoint -> handler);

        Mockito.when(session.getAsyncRemote()).thenReturn(remote);
        Mockito.when(session.getMessageHandlers()).thenReturn(Collections.emptySet());

        manager.attach(session).dispose();

        assertTrue(handler.closed);
        Mockito.verify(session).removeMessageHandler(handler);
    }

    @Test
    public void disposeIgnoresCloseAndRemoveFailures() {
        Session session = Mockito.mock(Session.class);
        Async remote = Mockito.mock(Async.class);
        ThrowingCloseHandler handler = new ThrowingCloseHandler();
        SimpleSessionManager<String> manager = new SimpleSessionManager<>(endpoint -> handler);

        Mockito.when(session.getAsyncRemote()).thenReturn(remote);
        Mockito.when(session.getMessageHandlers()).thenReturn(Set.of(new ThrowingWrappedHandler()));
        Mockito.doThrow(new IllegalStateException("remove failed")).when(session).removeMessageHandler(handler);

        manager.attach(session).dispose();

        assertTrue(handler.closeAttempted);
    }

    private static final class TestMessageHandler implements CloseableMessageHandler<String> {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
        }

        @Override
        public void onMessage(String message) {
        }
    }

    public static final class WrappedHandler implements MessageHandler {
        private final MessageHandler wrappedHandler;

        private WrappedHandler(MessageHandler wrappedHandler) {
            this.wrappedHandler = wrappedHandler;
        }

        public MessageHandler getWrappedHandler() {
            return wrappedHandler;
        }
    }

    private static final class ThrowingCloseHandler implements CloseableMessageHandler<String> {
        private boolean closeAttempted;

        @Override
        public void close() throws IOException {
            closeAttempted = true;
            throw new IOException("close failed");
        }

        @Override
        public void onMessage(String message) {
        }
    }

    public static final class ThrowingWrappedHandler implements MessageHandler {
        public MessageHandler getWrappedHandler() {
            throw new IllegalStateException("wrapper failed");
        }
    }
}
