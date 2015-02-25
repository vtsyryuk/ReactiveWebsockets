package wsx;

import com.google.common.base.Preconditions;
import rx.Subscription;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

import javax.websocket.MessageHandler;
import javax.websocket.Session;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;

public class SimpleSessionManager<T> implements SessionManager {

    private static final Object[] EMPTY_ARRAY = new Object[0];
    private MessageHandlerFactory<T> messageHandlerFactory;

    public SimpleSessionManager(final MessageHandlerFactory<T> messageHandlerFactory) {
        Preconditions.checkNotNull(messageHandlerFactory);
        this.messageHandlerFactory = messageHandlerFactory;
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
            } catch (Exception ignored) {
            }
        }
        return handler;
    }

    @SuppressWarnings("resource")
    @Override
    public Subscription attach(final Session session) {
        final CloseableMessageHandler<T> handler = messageHandlerFactory.create(session.getAsyncRemote());
        session.addMessageHandler(handler);
        final MessageHandler registeredHandler = getRegisteredHandler(session, handler);

        return Subscriptions.create(new Action0() {
            @Override
            public void call() {
                tryCloseMessageHandler(handler);
                tryRemoveMessageHandler(session, registeredHandler);
            }

            private void tryCloseMessageHandler(final Closeable closeable) {
                try {
                    closeable.close();
                } catch (IOException ignored) {
                }
            }

            private void tryRemoveMessageHandler(final Session session, final MessageHandler registeredHandler) {
                if (registeredHandler != null) {
                    try {
                        session.removeMessageHandler(registeredHandler);
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }
}
