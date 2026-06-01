package wsx;

import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.Subject;
import io.reactivex.rxjava3.subjects.PublishSubject;

import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles subscribe and unsubscribe requests by managing data-source subscriptions.
 */
public final class RequestMessageHandler implements CloseableMessageHandler<RequestMessage> {

    private static final String UnableToParseCommand = "Unable to parse a command";

    private final Async clientEndpoint;
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final ConcurrentHashMap<MessageSubject, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final Subject<RequestMessage> messageSubject = PublishSubject.<RequestMessage>create().toSerialized();

    private final Disposable handlerDisposable;

    /**
     * Creates a request message handler.
     *
     * @param clientEndpoint remote endpoint used to send reply messages
     * @param dataSource data source used to resolve subscribed subjects
     * @param diagnosticPublisher publisher for diagnostics
     * @param scheduler scheduler used for request processing
     */
    public RequestMessageHandler(final Async clientEndpoint,
                                 final DataSource dataSource,
                                 final Observer<DiagnosticMessage> diagnosticPublisher,
                                 final Scheduler scheduler) {

        Objects.requireNonNull(clientEndpoint, "clientEndpoint");
        Objects.requireNonNull(dataSource, "dataSource");
        Objects.requireNonNull(diagnosticPublisher, "diagnosticPublisher");
        Objects.requireNonNull(scheduler, "scheduler");

        this.clientEndpoint = clientEndpoint;
        this.dataSource = dataSource;
        this.diagnosticPublisher = diagnosticPublisher;

        handlerDisposable = messageSubject
                .observeOn(scheduler)
                .subscribe(this::handleMessage);
    }

    /**
     * Creates a websocket send callback that emits diagnostic messages for the result.
     *
     * @param subject subject being sent
     * @param diagnosticSubject publisher for send diagnostics
     * @return send result handler
     */
    protected static SendHandler createSendHandler(final MessageSubject subject,
                                                   final Observer<DiagnosticMessage> diagnosticSubject) {
        return new SendHandler() {

            @Override
            public void onResult(SendResult result) {
                if (result.isOK()) {
                    String message = String.format("Successfully sent message to the %s subject", subject);
                    diagnosticSubject.onNext(new DiagnosticMessage(DiagnosticLevel.DEBUG, message));
                } else {
                    String message = String.format("Failed to send message to the %s subject due to: %s", subject,
                            sendFailure(result));
                    diagnosticSubject.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, message));
                }
            }
        };
    }

    private static String sendFailure(SendResult result) {
        Throwable exception = result.getException();
        if (exception == null) {
            return "unknown send failure";
        }
        return exception.getCause() != null ? exception.getCause().toString() : exception.toString();
    }

    /**
     * Disposes all active subscriptions and stops request processing.
     *
     * @throws IOException never thrown by the current implementation
     */
    @Override
    public void close() throws IOException {
        for (Entry<MessageSubject, Disposable> entry : subscriptions.entrySet()) {
            entry.getValue().dispose();
        }
        subscriptions.clear();
        handlerDisposable.dispose();
    }

    /**
     * Queues an inbound request for scheduled processing.
     *
     * @param request inbound request message
     */
    @Override
    public void onMessage(final RequestMessage request) {
        messageSubject.onNext(request);
    }

    /**
     * Processes a request message and dispatches it to the matching command handler.
     *
     * @param request request to process
     */
    protected void handleMessage(final RequestMessage request) {
        String msg = String.format("Got request: %s", request);
        diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.DEBUG, msg));
        try {
            if (request.getContent() == null) {
                handleUnknown(request);
            } else if (request.getContent().isSubscribe()) {
                handleSubscribe(request);
            } else if (request.getContent().isUnsubscribe()) {
                handleUnsubscribe(request);
            }
        } catch (Exception e) {
            handleException(request, e);
        }
    }

    private void handleException(final RequestMessage request, final Exception e) {
        final String message = String.format("Failed to process %s command due to: %s", request.getContent(), e.getMessage());
        diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.ERROR, message));

        reply(request.getSubject(), message);
    }

    private void handleUnknown(final RequestMessage request) {
        diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, UnableToParseCommand));
        reply(request.getSubject(), UnableToParseCommand);
    }

    private void handleSubscribe(final RequestMessage request) {
        final MessageSubject subject = request.getSubject();

        if (subscriptions.containsKey(subject)) {
            String message = String.format("Duplicate %s subscribe command has been ignored", subject);
            diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, message));
        } else {
            AtomicBoolean created = new AtomicBoolean(false);
            subscriptions.computeIfAbsent(subject, key -> {
                created.set(true);
                return createSubscription(key);
            });
            if (created.get()) {
                String message = String.format("Subscribed to %s", subject);
                diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.INFO, message));
            } else {
                String message = String.format("Duplicate %s subscribe command has been ignored", subject);
                diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, message));
            }
        }
    }

    private void handleUnsubscribe(final RequestMessage request) {
        final MessageSubject subject = request.getSubject();
        Disposable s = subscriptions.remove(subject);
        if (s != null) {
            s.dispose();
        } else {
            String message = String.format("Unable to find %s subscription", subject);
            diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, message));
        }
    }

    @SuppressWarnings("synthetic-access")
    private Disposable createSubscription(final MessageSubject subject) {
        final SendHandler sendHandler = createSendHandler(subject, diagnosticPublisher);

        var onNext = (io.reactivex.rxjava3.functions.Consumer<ReplyMessage>) message -> {
            String msg = String.format("sending reply: %s", message);
            diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.DEBUG, msg));
            clientEndpoint.sendObject(message, sendHandler);
        };
        var onError = (io.reactivex.rxjava3.functions.Consumer<Throwable>) t ->
                diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.ERROR, t.getMessage()));
        var onComplete = (io.reactivex.rxjava3.functions.Action) () ->
                diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.INFO,
                        "The source sequence is completed"));
        return dataSource.getDataStream(subject)
                .subscribe(onNext, onError, onComplete);
    }

    private void reply(final MessageSubject subject, final String content) {
        final ReplyMessage response = ReplyMessage.create(subject, content);
        clientEndpoint.sendObject(response, createSendHandler(subject, diagnosticPublisher));
    }
}
