package wsx;

import com.google.common.base.Preconditions;
import rx.Observable;
import rx.Observer;
import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.io.IOException;

public class ReplyMessageHandler implements CloseableMessageHandler<ReplyMessage> {

    private final Subscription requestStreamSubscription;
    private final PublishSubject<ReplyMessage> replySubject = PublishSubject.create();
    private final Subscription replyStreamSubscription;

    public ReplyMessageHandler(final Async serverEndpoint,
                               final Observable<RequestMessage> requestStream,
                               final Observer<ReplyMessage> replyMessagePublisher,
                               final Observer<DiagnosticMessage> diagnosticPublisher,
                               final Scheduler scheduler) {

        Preconditions.checkNotNull(serverEndpoint);
        Preconditions.checkNotNull(requestStream);
        Preconditions.checkNotNull(replyMessagePublisher);
        Preconditions.checkNotNull(diagnosticPublisher);
        Preconditions.checkNotNull(scheduler);


        replyStreamSubscription = replySubject
                .observeOn(scheduler)
                .subscribe(replyMessagePublisher);

        this.requestStreamSubscription = requestStream.subscribe(new Action1<RequestMessage>() {
            @SuppressWarnings("synthetic-access")
            @Override
            public void call(RequestMessage msg) {
                final MessageSubject subject = msg.getSubject();
                serverEndpoint.sendObject(msg, createHandler(subject, diagnosticPublisher));
            }
        });
    }

    private static SendHandler createHandler(final MessageSubject subject, final Observer<DiagnosticMessage> diagnosticPublisher) {
        return new SendHandler() {

            @Override
            public void onResult(SendResult result) {
                if (result.isOK()) {
                    String message = String.format("Successfully sent a request message to the %s subject", subject);
                    diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.DEBUG, message));
                } else {
                    String message = String.format("Failed to publish a request message to the %s subject due to: %s", subject,
                            result.getException().getCause());
                    diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, message));
                }
            }
        };
    }

    @Override
    public void close() throws IOException {
        requestStreamSubscription.unsubscribe();
        replyStreamSubscription.unsubscribe();
    }

    @Override
    public void onMessage(final ReplyMessage message) {
        replySubject.onNext(message);
    }
}
