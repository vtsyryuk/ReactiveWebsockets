package wsx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;

import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.SendHandler;
import java.io.IOException;

public final class RequestMessageHandlerTest {

    private RequestMessageHandler msgHandler;
    private SubscriptionRouter routerMock;
    private PublishSubject<ReplyMessage> mockDataStream;

    private Async clientEndpointMock;
    private int dataSubscriptionCount;

    @BeforeEach
    @SuppressWarnings("synthetic-access")
    public void setUp() {
        clientEndpointMock = Mockito.mock(Async.class);
        routerMock = Mockito.mock(SubscriptionRouter.class);
        mockDataStream = PublishSubject.create();
        dataSubscriptionCount = 0;
        Observable<ReplyMessage> textStream = mockDataStream
                .doOnSubscribe(disposable -> dataSubscriptionCount++)
                .doOnDispose(() -> dataSubscriptionCount--);
        Mockito.when(routerMock.getDataStream(Mockito.any(MessageSubject.class))).thenReturn(textStream);
        PublishSubject<DiagnosticMessage> diagnosticSubjectMock = PublishSubject.create();
        msgHandler = new RequestMessageHandler(clientEndpointMock, routerMock, diagnosticSubjectMock, Schedulers.trampoline());
    }

    @Test
    public void subscribeUnsubscribeTest() {
        RequestMessage msg = new RequestMessage();
        MessageSubject subject = MessageSubjectFactory.create("Subject", "testSubject1");
        msg.setContent(RequestMessageType.Subscribe);
        msg.setSubject(subject);

        msgHandler.onMessage(msg);

        Mockito.verify(routerMock, Mockito.times(1)).getDataStream(subject);
        assertEquals(1, dataSubscriptionCount);

        String content = "testContent1";
        mockDataStream.onNext(ReplyMessage.create(subject, content));

        Mockito.verify(clientEndpointMock, Mockito.times(1))
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));


        msg.setContent(RequestMessageType.Unsubscribe);
        msgHandler.onMessage(msg);
        mockDataStream.onNext(ReplyMessage.create(subject, content));
        Mockito.verify(clientEndpointMock, Mockito.times(1))
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));
        assertEquals(0, dataSubscriptionCount);
    }

    @Test
    public void duplicateCommandTest() {
        RequestMessage msg = new RequestMessage();
        MessageSubject key = MessageSubjectFactory.create("Subject", "testSubject1");
        msg.setContent(RequestMessageType.Subscribe);
        msg.setSubject(key);

        try {
            msgHandler.onMessage(msg);
            msgHandler.onMessage(msg);
        } catch (Exception e) {
            fail("Duplicate subscribe command having the same subject should not throw");
        }

        String content = "testContent1";
        mockDataStream.onNext(ReplyMessage.create(key, content));

        Mockito.verify(clientEndpointMock, Mockito.times(1))
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));

        msg.setContent(RequestMessageType.Unsubscribe);
        try {
            msgHandler.onMessage(msg);
            msgHandler.onMessage(msg);
        } catch (Exception e) {
            fail("Duplicate unsubscribe command having the same subject should not throw");
        }
        mockDataStream.onNext(ReplyMessage.create(key, content));
        Mockito.verify(clientEndpointMock, Mockito.times(1))
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));
    }

    @Test
    public void closeTest() {
        MessageSubject subject = MessageSubjectFactory.create("Subject", "TestSubject1");
        RequestMessage msg = RequestMessage.create(subject, RequestMessageType.Subscribe);
        msgHandler.onMessage(msg);


        String content = "TestContent";
        mockDataStream.onNext(ReplyMessage.create(subject, content));

        Mockito.verify(clientEndpointMock, Mockito.times(1))
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));

        try {
            msgHandler.close();
        } catch (IOException e) {
            fail("close thrown unexpected exception");
        }

        mockDataStream.onNext(ReplyMessage.create(subject, content));
        Mockito.verify(clientEndpointMock, Mockito.times(1))
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));

    }
}
