package wsx;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Maintains a client websocket connection and retries connection attempts after closures.
 */
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
    private final Disposable closeDisposable;
    private final SerialDisposable scheduledReconnectAttempt = new SerialDisposable();
    // Tracks the live connection owned by this controller, including late connections racing with close().
    private final AtomicReference<Session> activeServerSession = new AtomicReference<>();
    // Set once by close(); timer callbacks use it to stop retrying and close late sessions.
    private final AtomicBoolean closeRequested = new AtomicBoolean();

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

    private void connectToServer() {
        if (closeRequested.get()) {
            return;
        }

        final Observable<Long> timer = Observable
                .interval(delay, period, unit, scheduler);

        Disposable reconnectAttempt = timer
                .filter(tick -> attemptConnection())
                .take(1)
                .subscribe();
        scheduledReconnectAttempt.set(reconnectAttempt);
    }

    private boolean attemptConnection() {
        if (closeRequested.get()) {
            return true;
        }
        Session connectedSession = null;
        try {
            connectedSession = container.connectToServer(clientEndpoint, clientConfig, serverUri);
            greetingAction.run();
            keepConnectedSession(connectedSession);
            return true;
        } catch (Throwable e) {
            closeSession(connectedSession);
            diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.ERROR, e.getMessage()));
            return false;
        }
    }

    private void keepConnectedSession(final Session connectedSession) {
        if (closeRequested.get()) {
            closeSession(connectedSession);
            return;
        }
        Session previousSession = activeServerSession.getAndSet(connectedSession);
        if (closeRequested.get()) {
            Session currentSession = activeServerSession.getAndSet(null);
            closeSession(currentSession);
        }
        if (previousSession != null && previousSession != connectedSession && previousSession.isOpen()) {
            closeSession(previousSession);
        }
    }

    /**
     * Stops reconnect attempts and closes the active server session when present.
     *
     * @throws IOException declared by {@link Closeable}; close failures are published as diagnostics
     */
    @Override
    public void close() throws IOException {
        if (!closeRequested.compareAndSet(false, true)) {
            return;
        }
        closeDisposable.dispose();
        scheduledReconnectAttempt.dispose();
        closeSession(activeServerSession.getAndSet(null));
    }

    private void closeSession(final Session session) {
        if (session == null || !session.isOpen()) {
            return;
        }
        try {
            session.close();
        } catch (IOException e) {
            diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.ERROR, e.getMessage()));
        }
    }

    /**
     * Builder for configuring automatic websocket reconnection behavior.
     */
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

        /**
         * Creates a builder with the required connection dependencies.
         *
         * @param container websocket container used to connect to the server
         * @param clientEndpoint endpoint instance attached to the connection
         * @param clientConfig client endpoint configuration
         * @param serverUri target server URI
         * @param replyPublisher publisher that receives connection status replies
         */
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

        /**
         * Sets the scheduler used for reconnect polling.
         *
         * @param scheduler RxJava scheduler for reconnect attempts
         * @return this builder
         */
        public Builder scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
            return this;
        }

        /**
         * Sets the initial reconnect delay.
         *
         * @param delay delay before the first reconnect attempt
         * @return this builder
         */
        public Builder delay(final long delay) {
            checkArgument(delay >= 0, "delay must be non-negative");
            this.delay = delay;
            return this;
        }

        /**
         * Sets the interval between reconnect attempts.
         *
         * @param period reconnect period
         * @return this builder
         */
        public Builder period(final long period) {
            checkArgument(period > 0, "period must be positive");
            this.period = period;
            return this;
        }

        /**
         * Sets the time unit used by delay and period.
         *
         * @param unit reconnect timing unit
         * @return this builder
         */
        public Builder timeUnit(final TimeUnit unit) {
            this.unit = Objects.requireNonNull(unit, "unit");
            return this;
        }

        /**
         * Sets an action invoked after a successful reconnect.
         *
         * @param greetingAction action to run after connection
         * @return this builder
         */
        public Builder greetingAction(final Action greetingAction) {
            this.greetingAction = greetingAction != null ? greetingAction : new Noop();
            return this;
        }

        /**
         * Sets the diagnostic publisher used for reconnect failures and close errors.
         *
         * @param diagnosticPublisher diagnostic message publisher
         * @return this builder
         */
        public Builder diagnosticPublisher(final Observer<DiagnosticMessage> diagnosticPublisher) {
            this.diagnosticPublisher = Objects.requireNonNull(diagnosticPublisher, "diagnosticPublisher");
            return this;
        }

        /**
         * Creates and starts the auto-reconnection controller.
         *
         * @return configured auto-reconnection instance
         */
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
            // Optional lifecycle hook; default action intentionally does nothing.
        }
    }
}
