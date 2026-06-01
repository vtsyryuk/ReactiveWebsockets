package wsx;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;

import javax.websocket.RemoteEndpoint.Async;
import java.util.Objects;

public final class ReplyMessageHandlerFactory implements MessageHandlerFactory<ReplyMessage> {

    private final Observable<RequestMessage> requestStream;
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final Scheduler scheduler;
    private final Observer<ReplyMessage> replyMessagePublisher;

    public ReplyMessageHandlerFactory(
            final Observable<RequestMessage> requestStream,
            final Observer<ReplyMessage> replyMessagePublisher,
            final Observer<DiagnosticMessage> diagnosticPublisher,
            final Scheduler scheduler) {

        this.replyMessagePublisher = Objects.requireNonNull(replyMessagePublisher, "replyMessagePublisher");
        this.requestStream = Objects.requireNonNull(requestStream, "requestStream");
        this.diagnosticPublisher = Objects.requireNonNull(diagnosticPublisher, "diagnosticPublisher");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CloseableMessageHandler<ReplyMessage> create(Async remoteEndpoint) {
        return new ReplyMessageHandler(remoteEndpoint, requestStream, replyMessagePublisher, diagnosticPublisher, scheduler);
    }

}
