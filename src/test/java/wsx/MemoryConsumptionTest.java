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

    private SocketEndpoint skywebClient;
    private SocketEndpoint skywebServer;

    private ReplyMessageService textMessageService;

    private SubscriptionRouter router;
    private Observer<DiagnosticMessage> diagnosticPublisher;
    private Scheduler scheduler;

    private RequestMessageHandler browserHandler1;
    private ReplyMessageHandler curvePublisherHandler;

    private Session browserSession1;
    private Session curvePublisherSession;
    private Async browserEndpoint1;
    private Async curvePublisherEndpoint;

    @Before
    public void setUp() {
        scheduler = Schedulers.immediate();
        diagnosticPublisher = new DiagnosticMessageService().getPublisher();
        textMessageService = new ReplyMessageService();

        router = new SubscriptionRouter(textMessageService.getStream());

        RequestMessageHandlerFactory requestMessageHandlerFactory =
                new RequestMessageHandlerFactory(router, diagnosticPublisher, scheduler);

        ReplyMessageHandlerFactory textMessageHandlerFactory =
                new ReplyMessageHandlerFactory(
                        router.getRequestStream(),
                        textMessageService.getPublisher(), diagnosticPublisher, scheduler);

        SessionManager curvePublisherSessionManager = new ReplyStreamSessionManager(textMessageHandlerFactory);
        SessionManager browserSessionManager = new RequestStreamSessionManager(requestMessageHandlerFactory);

        skywebClient = new SocketEndpoint(curvePublisherSessionManager, diagnosticPublisher);
        skywebServer = new SocketEndpoint(browserSessionManager, diagnosticPublisher);

        browserSession1 = Mockito.mock(Session.class);

        curvePublisherSession = Mockito.mock(Session.class);

        browserEndpoint1 = Mockito.mock(Async.class);

        curvePublisherEndpoint = Mockito.mock(Async.class);

        Mockito.when(browserSession1.getAsyncRemote()).thenReturn(browserEndpoint1);
        Mockito.when(browserSession1.getId()).thenReturn("1");

        Mockito.when(curvePublisherSession.getAsyncRemote()).thenReturn(curvePublisherEndpoint);
        Mockito.when(curvePublisherSession.getId()).thenReturn("3");

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
                curvePublisherHandler = (ReplyMessageHandler) invocation.getArguments()[0];
                return null;
            }
        })
                .when(curvePublisherSession)
                .addMessageHandler(Mockito.any(MessageHandler.class));
    }

    @Test
    public void testBasicFlow() {

        EndpointConfig endpointConfig = Mockito.mock(EndpointConfig.class);

        browserHandler1 = null;

        skywebServer.onOpen(browserSession1, endpointConfig);

        curvePublisherHandler = null;
        skywebClient.onOpen(curvePublisherSession, endpointConfig);

        Assert.assertNotNull(browserHandler1);
        Assert.assertNotNull(curvePublisherHandler);

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
            curvePublisherHandler.onMessage(msgConf);
            curvePublisherHandler.onMessage(msgText);
            browserHandler1.onMessage(msgUnsub);
        }
    }
}
