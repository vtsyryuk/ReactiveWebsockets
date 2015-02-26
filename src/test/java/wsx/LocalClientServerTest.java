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
import javax.websocket.SendHandler;
import javax.websocket.Session;

@SuppressWarnings({"synthetic-access"})
public final class LocalClientServerTest {

    private SocketEndpoint webappClient;
    private SocketEndpoint webappServer;

    private RequestMessageHandler browserHandler1;
    private RequestMessageHandler browserHandler2;
    private ReplyMessageHandler webappHandler;

    private Session browserSession1;
    private Session browserSession2;
    private Session webappSession;
    private Async webappEndpoint;
    private Async browserEndpoint1;
    private Async browserEndpoint2;

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

        SessionManager webappSessionManager = new ReplyStreamSessionManager(textMessageHandlerFactory);
        SessionManager browserSessionManager = new RequestStreamSessionManager(requestMessageHandlerFactory);

        webappClient = new SocketEndpoint(webappSessionManager, diagnosticPublisher);
        webappServer = new SocketEndpoint(browserSessionManager, diagnosticPublisher);

        browserSession1 = Mockito.mock(Session.class);
        browserSession2 = Mockito.mock(Session.class);
        webappSession = Mockito.mock(Session.class);

        browserEndpoint1 = Mockito.mock(Async.class);
        browserEndpoint2 = Mockito.mock(Async.class);
        webappEndpoint = Mockito.mock(Async.class);

        Mockito.when(browserSession1.getAsyncRemote()).thenReturn(browserEndpoint1);
        Mockito.when(browserSession1.getId()).thenReturn("1");
        Mockito.when(browserSession2.getAsyncRemote()).thenReturn(browserEndpoint2);
        Mockito.when(browserSession2.getId()).thenReturn("2");
        Mockito.when(webappSession.getAsyncRemote()).thenReturn(webappEndpoint);
        Mockito.when(webappSession.getId()).thenReturn("3");

        Mockito.doAnswer(new Answer<RequestMessageHandler>() {
            @Override
            public RequestMessageHandler answer(InvocationOnMock invocation) throws Throwable {
                browserHandler1 = (RequestMessageHandler) invocation.getArguments()[0];
                return null;
            }
        })
                .when(browserSession1)
                .addMessageHandler(Mockito.any(MessageHandler.class));

        Mockito.doAnswer(new Answer<RequestMessageHandler>() {
            @Override
            public RequestMessageHandler answer(InvocationOnMock invocation) throws Throwable {
                browserHandler2 = (RequestMessageHandler) invocation.getArguments()[0];
                return null;
            }
        })
                .when(browserSession2)
                .addMessageHandler(Mockito.any(MessageHandler.class));

        Mockito.doAnswer(new Answer<ReplyMessageHandler>() {
            @Override
            public ReplyMessageHandler answer(InvocationOnMock invocation) throws Throwable {
                webappHandler = (ReplyMessageHandler) invocation.getArguments()[0];
                return null;
            }
        })
                .when(webappSession)
                .addMessageHandler(Mockito.any(MessageHandler.class));
    }

    @Test
    public void testBasicFlow() {

        EndpointConfig endpointConfig = Mockito.mock(EndpointConfig.class);

        browserHandler1 = null;
        browserHandler2 = null;

        webappServer.onOpen(browserSession1, endpointConfig);
        webappServer.onOpen(browserSession2, endpointConfig);

        webappHandler = null;
        webappClient.onOpen(webappSession, endpointConfig);

        Assert.assertNotNull(browserHandler1);
        Assert.assertNotNull(browserHandler2);
        Assert.assertNotNull(webappHandler);

        MessageSubject subject = MessageSubjectFactory.create("Subject", "Subject1");
        RequestMessage msgSub = RequestMessage.create(subject, RequestMessageType.Subscribe);
        RequestMessage msgUnsub = RequestMessage.create(subject, RequestMessageType.Unsubscribe);

        browserHandler1.onMessage(msgSub);

        Mockito.verify(webappEndpoint, Mockito.times(1))
                .sendObject(Mockito.refEq(msgSub, "timestamp"), Mockito.any(SendHandler.class));

        ReplyMessage msgConf = ReplyMessage.create(subject, "Subscribed to " + subject);
        webappHandler.onMessage(msgConf);

        ReplyMessage msgText = ReplyMessage.create(subject, "TestContent1");
        webappHandler.onMessage(msgText);

        Mockito.verify(browserEndpoint2, Mockito.never())
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));
        Mockito.verify(browserEndpoint1, Mockito.times(1))
                .sendObject(Mockito.eq(msgConf), Mockito.any(SendHandler.class));
        Mockito.verify(browserEndpoint1, Mockito.times(1))
                .sendObject(Mockito.eq(msgText), Mockito.any(SendHandler.class));

        browserHandler2.onMessage(msgSub);
        browserHandler1.onMessage(msgUnsub);

        Mockito.verify(webappEndpoint, Mockito.times(1))
                .sendObject(Mockito.refEq(msgSub, "timestamp"), Mockito.any(SendHandler.class));

        webappHandler.onMessage(msgText);

        Mockito.verify(browserEndpoint2, Mockito.times(1))
                .sendObject(Mockito.eq(msgConf), Mockito.any(SendHandler.class));
        Mockito.verify(browserEndpoint2, Mockito.times(1))
                .sendObject(Mockito.eq(msgText), Mockito.any(SendHandler.class));
        Mockito.verify(browserEndpoint1, Mockito.times(1))
                .sendObject(Mockito.eq(msgConf), Mockito.any(SendHandler.class));
        Mockito.verify(browserEndpoint1, Mockito.times(1))
                .sendObject(Mockito.eq(msgText), Mockito.any(SendHandler.class));

        browserHandler2.onMessage(msgUnsub);
        Mockito.verify(webappEndpoint, Mockito.times(1))
                .sendObject(Mockito.refEq(msgUnsub, "timestamp"), Mockito.any(SendHandler.class));

        webappHandler.onMessage(msgText);
        Mockito.verify(browserEndpoint2, Mockito.times(2))
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));
        Mockito.verify(browserEndpoint1, Mockito.times(2))
                .sendObject(Mockito.any(ReplyMessage.class), Mockito.any(SendHandler.class));
    }
}
