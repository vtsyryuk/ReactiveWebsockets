package wsx;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;

import javax.websocket.RemoteEndpoint.Async;
import java.util.Objects;

/**
 * Factory for handlers that bridge reply messages from websocket peers into RxJava streams.
 */
public final class ReplyMessageHandlerFactory implements MessageHandlerFactory<ReplyMessage> {

    private final Observable<RequestMessage> requestStream;
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final Scheduler scheduler;
    private final Observer<ReplyMessage> replyMessagePublisher;

    /**
     * Creates a reply handler factory.
     *
     * @param requestStream outbound request stream to forward to the remote endpoint
     * @param replyMessagePublisher publisher for inbound replies
     * @param diagnosticPublisher publisher for send diagnostics
     * @param scheduler scheduler used for inbound reply processing
     */
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

    /**
     * Creates a reply message handler bound to the remote endpoint.
     *
     * @param remoteEndpoint endpoint used to send request messages
     * @return configured reply handler
     */
    @Override
    public CloseableMessageHandler<ReplyMessage> create(Async remoteEndpoint) {
        return new ReplyMessageHandler(remoteEndpoint, requestStream, replyMessagePublisher, diagnosticPublisher, scheduler);
    }

}
