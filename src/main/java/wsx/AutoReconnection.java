package wsx;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.schedulers.Schedulers;

import javax.websocket.ClientEndpointConfig;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("synthetic-access")
public class AutoReconnection implements Closeable {

    private static final MessageSubject CONNECTION_STATUS_SUBJECT = MessageSubject.of("Connection", "Status");
    private final WebSocketContainer container;
    private final SocketEndpoint clientEndpoint;
    private final ClientEndpointConfig clientConfig;
    private final URI serverUri;
    private final Action greetingAction;
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final Scheduler scheduler;
    private final long delay;
    private final long period;
    private final TimeUnit unit;
    private Disposable closeDisposable;
    private Disposable reconnectDisposable;
    private Session serverSession;
    private boolean closed;

    private AutoReconnection(final Builder builder) {

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

        this.closeDisposable = clientEndpoint.getCloseObservable().subscribe(closedSession -> {
            ReplyMessage msg = ReplyMessage.create(CONNECTION_STATUS_SUBJECT,
                    closedSession.closeReason().getReasonPhrase());
            builder.replyPublisher.onNext(msg);
            connectToServer();
        });

        connectToServer();
    }

    private synchronized void connectToServer() {
        if (closed) {
            return;
        }
        if (reconnectDisposable != null && !reconnectDisposable.isDisposed()) {
            reconnectDisposable.dispose();
        }

        final Observable<Long> timer = Observable
                .interval(delay, period, unit, scheduler);

        reconnectDisposable = timer
                .filter(tick -> {
                    try {
                        serverSession = container.connectToServer(clientEndpoint, clientConfig, serverUri);
                        greetingAction.run();
                        return true;
                    } catch (Throwable e) {
                        diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.ERROR, e.getMessage()));
                        return false;
                    }
                })
                .take(1)
                .subscribe();
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            closed = true;
            if (closeDisposable != null) {
                closeDisposable.dispose();
                closeDisposable = null;
            }
            if (reconnectDisposable != null) {
                reconnectDisposable.dispose();
                reconnectDisposable = null;
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
        private Action greetingAction = new Noop();
        private Observer<DiagnosticMessage> diagnosticPublisher = new DiagnosticMessageService().getPublisher();

        public Builder(final WebSocketContainer container,
                       final SocketEndpoint clientEndpoint,
                       final ClientEndpointConfig clientConfig,
                       final URI serverUri,
                       final Observer<ReplyMessage> replyPublisher) {

            Objects.requireNonNull(container, "container");
            Objects.requireNonNull(clientEndpoint, "clientEndpoint");
            Objects.requireNonNull(clientConfig, "clientConfig");
            Objects.requireNonNull(serverUri, "serverUri");
            Objects.requireNonNull(replyPublisher, "replyPublisher");

            this.container = container;
            this.clientEndpoint = clientEndpoint;
            this.clientConfig = clientConfig;
            this.serverUri = serverUri;
            this.replyPublisher = replyPublisher;
        }

        public Builder scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            return this;
        }

        public Builder delay(final long delay) {
            checkArgument(delay >= 0, "delay must be non-negative");
            this.delay = delay;
            return this;
        }

        public Builder period(final long period) {
            checkArgument(period > 0, "period must be positive");
            this.period = period;
            return this;
        }

        public Builder timeUnit(final TimeUnit unit) {
            this.unit = Objects.requireNonNull(unit, "unit");
            return this;
        }

        public Builder greetingAction(final Action greetingAction) {
            this.greetingAction = greetingAction != null ? greetingAction : new Noop();
            return this;
        }

        public Builder diagnosticPublisher(final Observer<DiagnosticMessage> diagnosticPublisher) {
            this.diagnosticPublisher = Objects.requireNonNull(diagnosticPublisher, "diagnosticPublisher");
            return this;
        }

        public AutoReconnection build() {
            return new AutoReconnection(this);
        }

        private static void checkArgument(boolean expression, String message) {
            if (!expression) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    private static final class Noop implements Action {
        @Override
        public void run() {
        }
    }
}
