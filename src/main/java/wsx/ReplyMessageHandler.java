package wsx;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.io.IOException;
import java.util.Objects;

public class ReplyMessageHandler implements CloseableMessageHandler<ReplyMessage> {

    private final Disposable requestStreamDisposable;
    private final Subject<ReplyMessage> replySubject = PublishSubject.<ReplyMessage>create().toSerialized();
    private final Disposable replyStreamDisposable;

    public ReplyMessageHandler(final Async serverEndpoint,
                               final Observable<RequestMessage> requestStream,
                               final Observer<ReplyMessage> replyMessagePublisher,
                               final Observer<DiagnosticMessage> diagnosticPublisher,
                               final Scheduler scheduler) {

        Objects.requireNonNull(serverEndpoint, "serverEndpoint");
        Objects.requireNonNull(requestStream, "requestStream");
        Objects.requireNonNull(replyMessagePublisher, "replyMessagePublisher");
        Objects.requireNonNull(diagnosticPublisher, "diagnosticPublisher");
        Objects.requireNonNull(scheduler, "scheduler");


        replyStreamDisposable = replySubject
                .observeOn(scheduler)
                .subscribe(
                        replyMessagePublisher::onNext,
                        replyMessagePublisher::onError,
                        replyMessagePublisher::onComplete);

        this.requestStreamDisposable = requestStream.subscribe(msg -> {
            final MessageSubject subject = msg.getSubject();
            serverEndpoint.sendObject(msg, createHandler(subject, diagnosticPublisher));
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
                            sendFailure(result));
                    diagnosticPublisher.onNext(new DiagnosticMessage(DiagnosticLevel.WARN, message));
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

    @Override
    public void close() throws IOException {
        requestStreamDisposable.dispose();
        replyStreamDisposable.dispose();
    }

    @Override
    public void onMessage(final ReplyMessage message) {
        replySubject.onNext(message);
    }
}
