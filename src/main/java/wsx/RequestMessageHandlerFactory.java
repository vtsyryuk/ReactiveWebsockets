package wsx;

import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;

import javax.websocket.RemoteEndpoint.Async;
import java.util.Objects;

public final class RequestMessageHandlerFactory implements MessageHandlerFactory<RequestMessage> {
    private final Observer<DiagnosticMessage> diagnosticPublisher;
    private final DataSource dataSource;
    private final Scheduler scheduler;

    public RequestMessageHandlerFactory(final DataSource dataSource,
                                        final Observer<DiagnosticMessage> diagnosticPublisher,
                                        final Scheduler scheduler) {

        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.diagnosticPublisher = Objects.requireNonNull(diagnosticPublisher, "diagnosticPublisher");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CloseableMessageHandler<RequestMessage> create(Async remoteEndpoint) {
        return new RequestMessageHandler(remoteEndpoint, dataSource, diagnosticPublisher, scheduler);
    }
}
