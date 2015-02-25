package wsx;

import com.google.common.base.Preconditions;
import rx.Observer;
import rx.Scheduler;

import javax.websocket.RemoteEndpoint.Async;

public final class RequestMessageHandlerFactory implements MessageHandlerFactory<RequestMessage> {
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final DataSource dataSource;
    private final Scheduler scheduler;

    public RequestMessageHandlerFactory(final DataSource dataSource,
                                        final Observer<DiagnosticMessage> diagnosticPublisher,
                                        final Scheduler scheduler) {

        Preconditions.checkNotNull(dataSource);
        Preconditions.checkNotNull(diagnosticPublisher);
        Preconditions.checkNotNull(scheduler);

        this.dataSource = dataSource;
        this.diagnosticPublisher = diagnosticPublisher;
        this.scheduler = scheduler;
    }

    @Override
    public CloseableMessageHandler<RequestMessage> create(Async remoteEndpoint) {
        return new RequestMessageHandler(remoteEndpoint, dataSource, diagnosticPublisher, scheduler);
    }
}