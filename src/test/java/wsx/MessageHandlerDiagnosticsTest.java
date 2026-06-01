package wsx;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MessageHandlerDiagnosticsTest {

    @Test
    public void requestSendHandlerPublishesSuccessAndFailureDiagnostics() {
        MessageSubject subject = MessageSubject.of("topic", "prices");
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        List<DiagnosticMessage> messages = new ArrayList<>();
        diagnostics.subscribe(messages::add);
        SendHandler handler = RequestMessageHandler.createSendHandler(subject, diagnostics);

        handler.onResult(new SendResult());
        handler.onResult(new SendResult(null));
        handler.onResult(new SendResult(new IllegalStateException("send failed")));
        handler.onResult(new SendResult(new RuntimeException("outer", new IllegalArgumentException("inner"))));

        assertEquals(DiagnosticLevel.DEBUG, messages.get(0).getLevel());
        assertEquals(DiagnosticLevel.WARN, messages.get(1).getLevel());
        assertEquals(DiagnosticLevel.WARN, messages.get(2).getLevel());
        assertEquals(DiagnosticLevel.WARN, messages.get(3).getLevel());
        assertTrue(messages.get(1).getMessage().contains("unknown send failure"));
        assertTrue(messages.get(2).getMessage().contains("send failed"));
        assertTrue(messages.get(3).getMessage().contains("inner"));
    }

    @Test
    public void requestHandlerRepliesToUnknownCommandAndStreamFailures() throws IOException {
        Async clientEndpoint = Mockito.mock(Async.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        List<DiagnosticMessage> messages = new ArrayList<>();
        diagnostics.subscribe(messages::add);
        RequestMessageHandler handler = new RequestMessageHandler(clientEndpoint, dataSource, diagnostics,
                Schedulers.trampoline());
        MessageSubject subject = MessageSubject.of("topic", "prices");

        RequestMessage unknown = RequestMessage.create(subject, null);
        handler.onMessage(unknown);

        RequestMessage subscribe = RequestMessage.create(subject, RequestMessageType.Subscribe);
        Mockito.when(dataSource.getDataStream(subject)).thenReturn(Observable.error(new IllegalStateException("boom")));
        handler.onMessage(subscribe);

        Mockito.verify(clientEndpoint, Mockito.atLeastOnce())
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));
        assertTrue(messages.stream().anyMatch(message -> message.getLevel() == DiagnosticLevel.WARN
                && message.getMessage().contains("Unable to parse")));
        assertTrue(messages.stream().anyMatch(message -> message.getLevel() == DiagnosticLevel.ERROR
                && message.getMessage().contains("boom")));
        handler.close();
    }

    @Test
    public void requestHandlerRepliesWhenSubscriptionCreationThrows() throws IOException {
        Async clientEndpoint = Mockito.mock(Async.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        List<DiagnosticMessage> messages = new ArrayList<>();
        diagnostics.subscribe(messages::add);
        RequestMessageHandler handler = new RequestMessageHandler(clientEndpoint, dataSource, diagnostics,
                Schedulers.trampoline());
        MessageSubject subject = MessageSubject.of("topic", "prices");

        Mockito.when(dataSource.getDataStream(subject)).thenThrow(new IllegalStateException("cannot subscribe"));
        handler.onMessage(RequestMessage.create(subject, RequestMessageType.Subscribe));

        Mockito.verify(clientEndpoint).sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));
        assertTrue(messages.stream().anyMatch(message -> message.getLevel() == DiagnosticLevel.ERROR
                && message.getMessage().contains("cannot subscribe")));
        handler.close();
    }

    @Test
    public void requestHandlerReportsCompletedSourceSequence() throws IOException {
        Async clientEndpoint = Mockito.mock(Async.class);
        DataSource dataSource = Mockito.mock(DataSource.class);
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        List<DiagnosticMessage> messages = new ArrayList<>();
        diagnostics.subscribe(messages::add);
        RequestMessageHandler handler = new RequestMessageHandler(clientEndpoint, dataSource, diagnostics,
                Schedulers.trampoline());
        MessageSubject subject = MessageSubject.of("topic", "prices");

        Mockito.when(dataSource.getDataStream(subject)).thenReturn(Observable.empty());
        handler.onMessage(RequestMessage.create(subject, RequestMessageType.Subscribe));

        assertTrue(messages.stream().anyMatch(message -> message.getLevel() == DiagnosticLevel.INFO
                && message.getMessage().contains("completed")));
        handler.close();
    }

    @Test
    public void replyHandlerForwardsRequestsAndPublishesReplies() throws IOException {
        Async serverEndpoint = Mockito.mock(Async.class);
        PublishSubject<RequestMessage> requestStream = PublishSubject.create();
        PublishSubject<ReplyMessage> replyPublisher = PublishSubject.create();
        PublishSubject<DiagnosticMessage> diagnostics = PublishSubject.create();
        List<ReplyMessage> replies = new ArrayList<>();
        List<DiagnosticMessage> diagnosticMessages = new ArrayList<>();
        replyPublisher.subscribe(replies::add);
        diagnostics.subscribe(diagnosticMessages::add);
        ReplyMessageHandler handler = new ReplyMessageHandler(serverEndpoint, requestStream, replyPublisher,
                diagnostics, Schedulers.trampoline());
        MessageSubject subject = MessageSubject.of("topic", "prices");

        requestStream.onNext(RequestMessage.create(subject, RequestMessageType.Subscribe));
        handler.onMessage(ReplyMessage.create(subject, "42"));

        ArgumentCaptor<SendHandler> sendHandlerCaptor = ArgumentCaptor.forClass(SendHandler.class);
        Mockito.verify(serverEndpoint).sendObject(Mockito.any(RequestMessage.class), sendHandlerCaptor.capture());
        sendHandlerCaptor.getValue().onResult(new SendResult());
        sendHandlerCaptor.getValue().onResult(new SendResult(null));
        sendHandlerCaptor.getValue().onResult(new SendResult(new IllegalStateException("send failed")));
        sendHandlerCaptor.getValue().onResult(new SendResult(new RuntimeException("outer",
                new IllegalArgumentException("inner"))));

        assertEquals(1, replies.size());
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.getLevel() == DiagnosticLevel.DEBUG));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.getMessage().contains("unknown send failure")));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.getMessage().contains("send failed")));
        assertTrue(diagnosticMessages.stream().anyMatch(message -> message.getMessage().contains("inner")));
        handler.close();
    }
}
