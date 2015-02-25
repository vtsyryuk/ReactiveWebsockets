package wsx;

import com.google.common.base.Preconditions;
import org.javatuples.Pair;
import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.CloseReason;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("synthetic-access")
public class AutoReconnection implements Closeable {

    private static final MessageSubject ConnStatusSubject = new MessageSubject(Pair.with("Connection", "Status"));
    private final WebSocketContainer container;
    private final SocketEndpoint clientEndpoint;
    private final ClientEndpointConfig clientConfig;
    private final URI serverUri;
    private final Action0 greetingAction;
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final Scheduler scheduler;
    private final long delay;
    private final long period;
    private final TimeUnit unit;
    private Subscription closeSubscription;
    private Session serverSession;

    public AutoReconnection(final Builder builder) {

        this.container = builder.container;
        this.clientEndpoint = builder.clientEndpoint;
        this.clientConfig = builder.clientConfig;
        this.serverUri = builder.serverUri;
        this.greetingAction = builder.greetingAction;

        this.diagnosticPublisher = builder.diagnosticPublisher;
        this.scheduler = builder.scheduler;
        this.delay = builder.delay;
        this.period = builder.period;
        this.unit = builder.unit;

        this.closeSubscription = clientEndpoint.getCloseObservable().subscribe(new Action1<Pair<Session, CloseReason>>() {

            @Override
            public void call(Pair<Session, CloseReason> p) {
                ReplyMessage msg = ReplyMessage.create(ConnStatusSubject, p.getValue1().getReasonPhrase());
                builder.replyPublisher.onNext(msg);
                connectToServer();
            }
        });

        connectToServer();
    }

    private void connectToServer() {

        final Observable<Long> timer = Observable
                .timer(delay, period, unit)
                .observeOn(scheduler);

        timer.takeFirst(new Func1<Long, Boolean>() {
            boolean requestsResent = false;

            @Override
            public Boolean call(Long t1) {
                try {
                    if (requestsResent) {
                        System.out.println("Unexpected timer event");
                        return Boolean.TRUE;
                    }
                    serverSession = container.connectToServer(clientEndpoint, clientConfig, serverUri);
                    greetingAction.call();
                    requestsResent = true;
                    return Boolean.TRUE;
                } catch (Exception e) {
                    diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.ERROR, e.getMessage()));
                    return Boolean.FALSE;
                }
            }
        }).subscribe();
    }

    @Override
    public void close() throws IOException {
        try {
            if (closeSubscription != null) {
                closeSubscription.unsubscribe();
                closeSubscription = null;
            }
            if (serverSession != null && serverSession.isOpen()) {
                serverSession.close();
                serverSession = null;
            }
        } catch (IOException e) {
            diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.ERROR, e.getMessage()));
        }
    }

    public static class Builder {
        // Required parameters
        private final WebSocketContainer container;
        private final SocketEndpoint clientEndpoint;
        private final ClientEndpointConfig clientConfig;
        private final URI serverUri;
        private final Observer<ReplyMessage> replyPublisher;

        // Optional parameters - initialized to default values
        private Scheduler scheduler = Schedulers.computation();
        private long delay = 0L;
        private long period = 5L;
        private TimeUnit unit = TimeUnit.SECONDS;
        private Action0 greetingAction = new Noop();
        private Observer<DiagnosticMessage> diagnosticPublisher = new DiagnosticMessageService().getPublisher();

        public Builder(final WebSocketContainer container,
                       final SocketEndpoint clientEndpoint,
                       final ClientEndpointConfig clientConfig,
                       final URI serverUri,
                       final Observer<ReplyMessage> replyPublisher) {

            Preconditions.checkNotNull(container);
            Preconditions.checkNotNull(clientEndpoint);
            Preconditions.checkNotNull(clientConfig);
            Preconditions.checkNotNull(serverUri);
            Preconditions.checkNotNull(replyPublisher);

            this.container = container;
            this.clientEndpoint = clientEndpoint;
            this.clientConfig = clientConfig;
            this.serverUri = serverUri;
            this.replyPublisher = replyPublisher;
        }

        public Builder scheduler(Scheduler scheduler) {
            Preconditions.checkNotNull(scheduler);
            this.scheduler = scheduler;
            return this;
        }

        public Builder delay(final long delay) {
            Preconditions.checkArgument(delay >= 0);
            this.delay = delay;
            return this;
        }

        public Builder period(final long period) {
            Preconditions.checkArgument(period >= 0);
            this.period = period;
            return this;
        }

        public Builder timeUnit(final TimeUnit unit) {
            Preconditions.checkNotNull(unit);
            this.unit = unit;
            return this;
        }

        public Builder greetingAction(final Action0 greetingAction) {
            this.greetingAction = greetingAction != null ? greetingAction : new Noop();
            return this;
        }

        public Builder timeUnit(final Observer<DiagnosticMessage> diagnosticPublisher) {
            Preconditions.checkNotNull(diagnosticPublisher);
            this.diagnosticPublisher = diagnosticPublisher;
            return this;
        }
    }

    private static final class Noop implements Action0 {
        public Noop() {
        }

        @Override
        public void call() {
        }
    }
}