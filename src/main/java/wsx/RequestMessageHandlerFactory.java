package wsx;

import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;

import javax.websocket.RemoteEndpoint.Async;
import java.util.Objects;

/**
 * Factory for handlers that process websocket subscription request messages.
 */
public final class RequestMessageHandlerFactory implements MessageHandlerFactory<RequestMessage> {
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final DataSource dataSource;
    private final Scheduler scheduler;

    /**
     * Creates a request handler factory.
     *
     * @param dataSource data source used to satisfy subscribe requests
     * @param diagnosticPublisher publisher for processing and send diagnostics
     * @param scheduler scheduler used for request processing
     */
    public RequestMessageHandlerFactory(final DataSource dataSource,
                                        final Observer<DiagnosticMessage> diagnosticPublisher,
                                        final Scheduler scheduler) {

        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.diagnosticPublisher = Objects.requireNonNull(diagnosticPublisher, "diagnosticPublisher");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Creates a request message handler bound to the remote endpoint.
     *
     * @param remoteEndpoint endpoint used to send reply messages
     * @return configured request handler
     */
    @Override
    public CloseableMessageHandler<RequestMessage> create(Async remoteEndpoint) {
        return new RequestMessageHandler(remoteEndpoint, dataSource, diagnosticPublisher, scheduler);
    }
}
