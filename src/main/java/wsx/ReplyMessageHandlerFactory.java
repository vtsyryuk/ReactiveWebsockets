package wsx;

import com.google.common.base.Preconditions;
import rx.Observable;
import rx.Observer;
import rx.Scheduler;

import javax.websocket.RemoteEndpoint.Async;

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

        Preconditions.checkNotNull(replyMessagePublisher);
        Preconditions.checkNotNull(requestStream);
        Preconditions.checkNotNull(diagnosticPublisher);
        Preconditions.checkNotNull(scheduler);

        this.replyMessagePublisher = replyMessagePublisher;
        this.requestStream = requestStream;
        this.diagnosticPublisher = diagnosticPublisher;
        this.scheduler = scheduler;
    }

    @Override
    public CloseableMessageHandler<ReplyMessage> create(Async remoteEndpoint) {
        return new ReplyMessageHandler(remoteEndpoint, requestStream, replyMessagePublisher, diagnosticPublisher, scheduler);
    }

}