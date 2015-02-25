package wsx;

import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.subjects.PublishSubject;

import javax.websocket.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class SocketEndpoint extends Endpoint {

    private final ConcurrentHashMap<String, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final SessionManager sessionManager;
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final PublishSubject<Pair<Session, CloseReason>> closeObservable;

    @Autowired
    public SocketEndpoint(final SessionManager sessionManager, final Observer<DiagnosticMessage> diagnosticPublisher) {
        this.sessionManager = sessionManager;
        this.diagnosticPublisher = diagnosticPublisher;
        this.closeObservable = PublishSubject.create();
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        final Subscription s = sessionManager.attach(session);
        subscriptions.put(session.getId(), s);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        final Subscription s = subscriptions.remove(session.getId());
        if (s != null) {
            s.unsubscribe();
        }
        String message = String.format("Connection id.%s closed %s", session.getId(), closeReason.getReasonPhrase());
        diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.INFO, message));
        closeObservable.onNext(Pair.with(session, closeReason));
    }

    @OnError
    public void onError(Session session, Throwable thr) {
        final DiagnosticMessage message = new DiagnosticMessage(DiagnosticLevel.ERROR, thr.getMessage());
        diagnosticPublisher.onNext(message);
    }

    public Observable<Pair<Session, CloseReason>> getCloseObservable() {
        return closeObservable.asObservable();
    }
}
