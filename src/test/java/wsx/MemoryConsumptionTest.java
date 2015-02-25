package wsx;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observer;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.Session;

@SuppressWarnings({"synthetic-access"})
public final class MemoryConsumptionTest {

    private SocketEndpoint clientToDataServer;
    private SocketEndpoint browserServer;

    private RequestMessageHandler browserHandler1;
    private ReplyMessageHandler dataServerHandler;

    private Session browserSession1;
    private Session dataServerSession;

    @Before
    public void setUp() {
        Scheduler scheduler = Schedulers.immediate();
        Observer<DiagnosticMessage> diagnosticPublisher = new DiagnosticMessageService().getPublisher();
        ReplyMessageService textMessageService = new ReplyMessageService();

        SubscriptionRouter router = new SubscriptionRouter(textMessageService.getStream());

        RequestMessageHandlerFactory requestMessageHandlerFactory =
                new RequestMessageHandlerFactory(router, diagnosticPublisher, scheduler);

        ReplyMessageHandlerFactory textMessageHandlerFactory =
                new ReplyMessageHandlerFactory(
                        router.getRequestStream(),
                        textMessageService.getPublisher(), diagnosticPublisher, scheduler);

        SessionManager dataServerSessionManager = new ReplyStreamSessionManager(textMessageHandlerFactory);
        SessionManager browserSessionManager = new RequestStreamSessionManager(requestMessageHandlerFactory);

        clientToDataServer = new SocketEndpoint(dataServerSessionManager, diagnosticPublisher);
        browserServer = new SocketEndpoint(browserSessionManager, diagnosticPublisher);

        browserSession1 = Mockito.mock(Session.class);

        dataServerSession = Mockito.mock(Session.class);

        Async browserEndpoint1 = Mockito.mock(Async.class);

        Async dataServerEndpoint = Mockito.mock(Async.class);

        Mockito.when(browserSession1.getAsyncRemote()).thenReturn(browserEndpoint1);
        Mockito.when(browserSession1.getId()).thenReturn("1");

        Mockito.when(dataServerSession.getAsyncRemote()).thenReturn(dataServerEndpoint);
        Mockito.when(dataServerSession.getId()).thenReturn("3");

        Mockito.doAnswer(new Answer<RequestMessageHandler>() {
            @Override
            public RequestMessageHandler answer(InvocationOnMock invocation) throws Throwable {
                browserHandler1 = (RequestMessageHandler) invocation.getArguments()[0];
                return null;
            }
        })
                .when(browserSession1)
                .addMessageHandler(Mockito.any(MessageHandler.class));


        Mockito.doAnswer(new Answer<ReplyMessageHandler>() {
            @Override
            public ReplyMessageHandler answer(InvocationOnMock invocation) throws Throwable {
                dataServerHandler = (ReplyMessageHandler) invocation.getArguments()[0];
                return null;
            }
        })
                .when(dataServerSession)
                .addMessageHandler(Mockito.any(MessageHandler.class));
    }

    @Test
    public void testBasicFlow() {

        EndpointConfig endpointConfig = Mockito.mock(EndpointConfig.class);

        browserHandler1 = null;

        browserServer.onOpen(browserSession1, endpointConfig);

        dataServerHandler = null;
        clientToDataServer.onOpen(dataServerSession, endpointConfig);

        Assert.assertNotNull(browserHandler1);
        Assert.assertNotNull(dataServerHandler);

        RequestMessage msgSub = new RequestMessage();
        MessageSubject subject = MessageSubjectFactory.create("Subject", "Subject1");
        msgSub.setContent(RequestMessageType.Subscribe);
        msgSub.setSubject(subject);

        RequestMessage msgUnsub = new RequestMessage();
        msgUnsub.setContent(RequestMessageType.Unsubscribe);
        msgUnsub.setSubject(subject);

        ReplyMessage msgConf = ReplyMessage.create(subject, "Subscribed to " + subject);
        ReplyMessage msgText = ReplyMessage.create(subject, "TestContent1");

        for (int i = 0; i < 10000; ++i) {
            browserHandler1.onMessage(msgSub);
            dataServerHandler.onMessage(msgConf);
            dataServerHandler.onMessage(msgText);
            browserHandler1.onMessage(msgUnsub);
        }
    }
}
