package wsx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

import javax.websocket.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Websocket endpoint that attaches sessions to a {@link SessionManager} and publishes lifecycle diagnostics.
 */
@Component
public final class SocketEndpoint extends Endpoint {

    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final SessionManager sessionManager;
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final Subject<ClosedSession> closeObservable;

    /**
     * Creates a socket endpoint.
     *
     * @param sessionManager manager used to attach message handlers to sessions
     * @param diagnosticPublisher publisher for endpoint diagnostics
     */
    @Autowired
    public SocketEndpoint(final SessionManager sessionManager, final Observer<DiagnosticMessage> diagnosticPublisher) {
        this.sessionManager = sessionManager;
        this.diagnosticPublisher = diagnosticPublisher;
        this.closeObservable = PublishSubject.<ClosedSession>create().toSerialized();
    }

    /**
     * Attaches the newly opened websocket session to the configured session manager.
     *
     * @param session opened websocket session
     * @param config endpoint configuration
     */
    @Override
    public void onOpen(Session session, EndpointConfig config) {
        final Disposable s = sessionManager.attach(session);
        subscriptions.put(session.getId(), s);
    }

    /**
     * Cleans up subscriptions and publishes a close event for the closed session.
     *
     * @param session closed websocket session
     * @param closeReason reason reported by the websocket runtime
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        final Disposable s = subscriptions.remove(session.getId());
        if (s != null) {
            s.dispose();
        }
        String message = String.format("Connection id.%s closed %s", session.getId(), closeReason.getReasonPhrase());
        diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.INFO, message));
        closeObservable.onNext(new ClosedSession(session, closeReason));
    }

    /**
     * Publishes websocket endpoint errors to the diagnostic stream.
     *
     * @param session session associated with the error
     * @param thr error reported by the websocket runtime
     */
    @OnError
    public void onError(Session session, Throwable thr) {
        final DiagnosticMessage message = new DiagnosticMessage(DiagnosticLevel.ERROR, thr.getMessage());
        diagnosticPublisher.onNext(message);
    }

    /**
     * Returns a stream of session close events.
     *
     * @return close event stream
     */
    public Observable<ClosedSession> getCloseObservable() {
        return closeObservable.hide();
    }

    /**
     * Close event containing the websocket session and its close reason.
     *
     * @param session closed websocket session
     * @param closeReason close reason reported by the runtime
     */
    public record ClosedSession(Session session, CloseReason closeReason) {
    }
}
