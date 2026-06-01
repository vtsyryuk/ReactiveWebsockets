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

@Component
public final class SocketEndpoint extends Endpoint {

    private final ConcurrentHashMap<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final SessionManager sessionManager;
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final Subject<ClosedSession> closeObservable;

    @Autowired
    public SocketEndpoint(final SessionManager sessionManager, final Observer<DiagnosticMessage> diagnosticPublisher) {
        this.sessionManager = sessionManager;
        this.diagnosticPublisher = diagnosticPublisher;
        this.closeObservable = PublishSubject.<ClosedSession>create().toSerialized();
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        final Disposable s = sessionManager.attach(session);
        subscriptions.put(session.getId(), s);
    }

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

    @OnError
    public void onError(Session session, Throwable thr) {
        final DiagnosticMessage message = new DiagnosticMessage(DiagnosticLevel.ERROR, thr.getMessage());
        diagnosticPublisher.onNext(message);
    }

    public Observable<ClosedSession> getCloseObservable() {
        return closeObservable.hide();
    }

    public record ClosedSession(Session session, CloseReason closeReason) {
    }
}
