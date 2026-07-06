package wsx;

import io.reactivex.rxjava3.disposables.Disposable;

import javax.websocket.MessageHandler;
import javax.websocket.Session;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Session manager that attaches one message handler to each websocket session.
 *
 * @param <T> message type handled by sessions managed by this instance
 */
public class SimpleSessionManager<T> implements SessionManager {

    private static final Object[] EMPTY_ARRAY = new Object[0];
    private final MessageHandlerFactory<T> messageHandlerFactory;

    /**
     * Creates a session manager that obtains handlers from the supplied factory.
     *
     * @param messageHandlerFactory factory used to create per-session handlers
     */
    public SimpleSessionManager(final MessageHandlerFactory<T> messageHandlerFactory) {
        this.messageHandlerFactory = Objects.requireNonNull(messageHandlerFactory, "messageHandlerFactory");
    }

    // NOTE: this workaround is because of the custom tomcat'c websocket implementation
    private static MessageHandler getRegisteredHandler(final Session session, final MessageHandler handler) {
        for (MessageHandler h : session.getMessageHandlers()) {
            try {
                Method m = h.getClass().getMethod("getWrappedHandler");
                Object clientMessageHandler = m.invoke(h, EMPTY_ARRAY);
                if (clientMessageHandler == handler) {
                    return h;
                }
            } catch (Exception _) {
                // Some websocket wrappers do not expose getWrappedHandler; fall back to the original handler.
            }
        }
        return handler;
    }

    /**
     * Attaches a handler to the session and returns cleanup logic for removal and close.
     *
     * @param session websocket session to attach to
     * @return disposable that detaches and closes the handler
     */
    @Override
    public Disposable attach(final Session session) {
        final CloseableMessageHandler<T> handler = messageHandlerFactory.create(session.getAsyncRemote());
        session.addMessageHandler(handler);
        final MessageHandler registeredHandler = getRegisteredHandler(session, handler);

        return Disposable.fromAction(() -> {
            tryCloseMessageHandler(handler);
            tryRemoveMessageHandler(session, registeredHandler);

        });
    }

    private static void tryCloseMessageHandler(final Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException _) {
            // Disposal should continue even if an application handler refuses to close.
        }
    }

    private static void tryRemoveMessageHandler(final Session session, final MessageHandler registeredHandler) {
        if (registeredHandler != null) {
            try {
                session.removeMessageHandler(registeredHandler);
            } catch (Exception _) {
                // Session cleanup is best-effort because containers can remove handlers during close.
            }
        }
    }
}
