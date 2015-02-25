package wsx;

import com.google.common.base.Preconditions;
import rx.Observer;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public final class RequestMessageHandler implements CloseableMessageHandler<RequestMessage> {

    private static final String UnableToParseCommand = "Unable to parse a command";

    private final Async clientEndpoint;
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final ConcurrentHashMap<MessageSubject, Subscription> subscriptions = new ConcurrentHashMap<>();
    private final DataSource dataSource;
    private final PublishSubject<RequestMessage> messageSubject = PublishSubject.create();

    private final Subscription handlerSubscription;

    public RequestMessageHandler(final Async clientEndpoint,
                                 final DataSource dataSource,
                                 final Observer<DiagnosticMessage> diagnosticPublisher,
                                 final Scheduler scheduler) {

        Preconditions.checkNotNull(clientEndpoint);
        Preconditions.checkNotNull(dataSource);
        Preconditions.checkNotNull(diagnosticPublisher);
        Preconditions.checkNotNull(scheduler);

        this.clientEndpoint = clientEndpoint;
        this.dataSource = dataSource;
        this.diagnosticPublisher = diagnosticPublisher;

        handlerSubscription = messageSubject
                .observeOn(scheduler)
                .subscribe(new Action1<RequestMessage>() {
                    @Override
                    public void call(RequestMessage m) {
                        handleMessage(m);
                    }
                });
    }

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
                            result.getException().getCause());
                    diagnosticSubject.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, message));
                }
            }
        };
    }

    @Override
    public void close() throws IOException {
        for (Entry<MessageSubject, Subscription> entry : subscriptions.entrySet()) {
            entry.getValue().unsubscribe();
        }
        subscriptions.clear();
        handlerSubscription.unsubscribe();
    }

    @Override
    public void onMessage(final RequestMessage request) {
        messageSubject.onNext(request);
    }

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

        Reply(request.getSubject(), message);
    }

    private void handleUnknown(final RequestMessage request) {
        diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, UnableToParseCommand));
        Reply(request.getSubject(), UnableToParseCommand);
    }

    private void handleSubscribe(final RequestMessage request) {
        final MessageSubject subject = request.getSubject();

        if (subscriptions.containsKey(subject)) {
            String message = String.format("Duplicate %s subscribe command has been ignored", subject);
            diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, message));
        } else {
            subscriptions.put(subject, createSubscription(subject));

            String message = String.format("Subscribed to %s", subject);
            diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.INFO, message));
        }
    }

    private void handleUnsubscribe(final RequestMessage request) {
        final MessageSubject subject = request.getSubject();
        Subscription s = subscriptions.remove(subject);
        if (s != null) {
            s.unsubscribe();
        } else {
            String message = String.format("Unable to find %s subscription", subject);
            diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, message));
        }
    }

    @SuppressWarnings("synthetic-access")
    private Subscription createSubscription(final MessageSubject subject) {
        final SendHandler sendHandler = createSendHandler(subject, diagnosticPublisher);

        final Action1<ReplyMessage> onNext = new Action1<ReplyMessage>() {
            @Override
            public void call(ReplyMessage message) {
                String msg = String.format("sending reply: %s", message);
                diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.DEBUG, msg));
                clientEndpoint.sendObject(message, sendHandler);
            }
        };
        final Action1<Throwable> onError = new Action1<Throwable>() {
            @Override
            public void call(Throwable t) {
                diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.ERROR, t.getMessage()));
            }
        };
        final Action0 onComplete = new Action0() {
            @Override
            public void call() {
                diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.INFO,
                        "The source sequence is completed"));
            }
        };
        return dataSource.getDataStream(subject)
                .subscribe(onNext, onError, onComplete);
    }

    private void Reply(final MessageSubject subject, final String content) {
        final ReplyMessage response = ReplyMessage.create(subject, content);
        clientEndpoint.sendObject(response, createSendHandler(subject, diagnosticPublisher));
    }
}