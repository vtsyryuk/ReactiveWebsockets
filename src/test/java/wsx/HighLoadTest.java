package wsx;

import org.javatuples.Pair;
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
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"synthetic-access"})
public final class HighLoadTest {

    private SocketEndpoint skywebClient;
    private SocketEndpoint skywebServer;
    private ReplyMessageService textMessageService;
    private SubscriptionRouter router;
    private Observer<DiagnosticMessage> diagnosticPublisher;
    private Scheduler scheduler;
    private ReplyMessageHandler curvePublisherHandler;
    private Session curvePublisherSession;
    private Async curvePublisherEndpoint;
    private int browserCount;
    private List<BrowserSessionMock> browsers;
    private int textMsgCount;
    private List<ReplyMessage> textMessages;
    private List<ReplyMessage> confMessages;
    private List<RequestMessage> subMessages;
    private List<RequestMessage> unsubMessages;
    private int subjectsPerBrowser;
    private int iterationCount = 10;

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

        browserCount = 100;
        browsers = new ArrayList<>(browserCount);
        subjectsPerBrowser = 10;

        textMsgCount = 100;
        textMessages = new ArrayList<>(textMsgCount);
        confMessages = new ArrayList<>(textMsgCount);
        subMessages = new ArrayList<>(textMsgCount);
        unsubMessages = new ArrayList<>(textMsgCount);


        for (int i = 0; i < browserCount; ++i) {
            final BrowserSessionMock b = new BrowserSessionMock();
            b.id = i;
            b.session = Mockito.mock(Session.class);
            b.endpoint = Mockito.mock(Async.class);

            Mockito.doAnswer(new Answer<RequestMessageHandler>() {
                @Override
                public RequestMessageHandler answer(InvocationOnMock invocation) throws Throwable {
                    b.handler = (RequestMessageHandler) invocation.getArguments()[0];
                    return null;
                }
            })
                    .when(b.session)
                    .addMessageHandler(Mockito.any(MessageHandler.class));

            Mockito.when(b.session.getAsyncRemote()).thenReturn(b.endpoint);
            Mockito.when(b.session.getId()).thenReturn(String.valueOf(i));

            int x = i;
            int y = i + subjectsPerBrowser;
            if (y >= textMsgCount) {
                y -= textMsgCount;
            }
            b.subjectRange = Pair.with(Integer.valueOf(x), Integer.valueOf(y));

            browsers.add(b);
        }

        curvePublisherSession = Mockito.mock(Session.class);
        curvePublisherEndpoint = Mockito.mock(Async.class);
        Mockito.when(curvePublisherSession.getAsyncRemote()).thenReturn(curvePublisherEndpoint);
        Mockito.when(curvePublisherSession.getId()).thenReturn(String.valueOf(browserCount + 1));

        Mockito.doAnswer(new Answer<ReplyMessageHandler>() {
            @Override
            public ReplyMessageHandler answer(InvocationOnMock invocation) throws Throwable {
                curvePublisherHandler = (ReplyMessageHandler) invocation.getArguments()[0];
                return null;
            }
        })
                .when(curvePublisherSession)
                .addMessageHandler(Mockito.any(MessageHandler.class));

        for (int i = 0; i < textMsgCount; ++i) {
            final MessageSubject subj = MessageSubjectFactory.create("Subject", "Subject" + i);
            ReplyMessage msg = ReplyMessage.create(subj, "Content" + i);
            textMessages.add(msg);

            ReplyMessage confMsg = ReplyMessage.create(subj, "Subscribed to " + subj);
            confMessages.add(confMsg);

            RequestMessage subMsg = RequestMessage.create(subj, RequestMessageType.Subscribe);
            subMessages.add(subMsg);

            RequestMessage unsubMsg = RequestMessage.create(subj, RequestMessageType.Unsubscribe);
            unsubMessages.add(unsubMsg);
        }
    }

    @Test
    public void testHighLoadBasicFlow() {

        EndpointConfig endpointConfig = Mockito.mock(EndpointConfig.class);

        for (BrowserSessionMock m : browsers) {
            m.handler = null;
        }

        for (BrowserSessionMock m : browsers) {
            skywebServer.onOpen(m.session, endpointConfig);
        }

        curvePublisherHandler = null;
        skywebClient.onOpen(curvePublisherSession, endpointConfig);

        for (BrowserSessionMock m : browsers) {
            Assert.assertNotNull(m.handler);
        }

        Assert.assertNotNull(curvePublisherHandler);

        // Subscribing each browser to a range of subjects
        for (BrowserSessionMock m : browsers) {
            final Pair<Integer, Integer> r = m.subjectRange;
            int i = r.getValue0().intValue();
            while (i != r.getValue1().intValue()) {
                m.handler.onMessage(subMessages.get(i));
                ++i;
                if (i >= textMsgCount) {
                    i -= textMsgCount;
                }
            }
        }

        for (int i = 0; i < textMsgCount; i += textMsgCount / 10) {
            Mockito.verify(curvePublisherEndpoint, Mockito.times(1))
                    .sendObject(Mockito.refEq(subMessages.get(i), "timestamp"), Mockito.any(SendHandler.class));
        }

        for (int i = 0; i < textMsgCount; ++i) {
            ReplyMessage msgConf = confMessages.get(i);
            curvePublisherHandler.onMessage(msgConf);
        }

        int messagesPerSubject = 5;

        for (int k = 0; k < messagesPerSubject; ++k) {
            for (int i = 0; i < textMsgCount; ++i) {
                ReplyMessage msgText = textMessages.get(i);
                curvePublisherHandler.onMessage(msgText);
            }
        }

        for (int k = 0; k < browserCount; k += browserCount / 10) {
            BrowserSessionMock m = browsers.get(k);
            final Pair<Integer, Integer> r = m.subjectRange;
            int i = r.getValue0().intValue();
            while (i != r.getValue1().intValue()) {
                Mockito.verify(m.endpoint, Mockito.times(1))
                        .sendObject(Mockito.eq(confMessages.get(i)), Mockito.any(SendHandler.class));
                Mockito.verify(m.endpoint, Mockito.times(messagesPerSubject))
                        .sendObject(Mockito.eq(textMessages.get(i)), Mockito.any(SendHandler.class));
                ++i;
                if (i >= textMsgCount) {
                    i -= textMsgCount;
                }
            }
        }

        // Unsubscribing browsers from all subjects except one
        for (BrowserSessionMock m : browsers) {
            final Pair<Integer, Integer> r = m.subjectRange;
            int i = r.getValue0().intValue() + 1;
            if (i >= textMsgCount) {
                i -= textMsgCount;
            }
            while (i != r.getValue1().intValue()) {
                m.handler.onMessage(unsubMessages.get(i));
                ++i;
                if (i >= textMsgCount) {
                    i -= textMsgCount;
                }
            }
        }

        for (int i = 0; i < textMsgCount; i += textMsgCount / 10) {
            Mockito.verify(curvePublisherEndpoint, Mockito.never())
                    .sendObject(Mockito.refEq(unsubMessages.get(i)), Mockito.any(SendHandler.class));
        }

        // Unsubscribing all
        for (BrowserSessionMock m : browsers) {
            final Pair<Integer, Integer> r = m.subjectRange;
            int i = r.getValue0().intValue();
            while (i != r.getValue1().intValue()) {
                m.handler.onMessage(unsubMessages.get(i));
                ++i;
                if (i >= textMsgCount) {
                    i -= textMsgCount;
                }
            }
        }

        for (int i = 0; i < textMsgCount; i += textMsgCount / 10) {
            Mockito.verify(curvePublisherEndpoint, Mockito.times(1))
                    .sendObject(Mockito.refEq(unsubMessages.get(i), "timestamp"), Mockito.any(SendHandler.class));
        }
    }

    @Test
    public void testMemory() {

        EndpointConfig endpointConfig = Mockito.mock(EndpointConfig.class);

        for (BrowserSessionMock m : browsers) {
            m.handler = null;
        }

        for (BrowserSessionMock m : browsers) {
            skywebServer.onOpen(m.session, endpointConfig);
        }

        curvePublisherHandler = null;
        skywebClient.onOpen(curvePublisherSession, endpointConfig);


        for (int j = 0; j < iterationCount; ++j) {
            // Subscribing each browser to a range of subjects
            for (BrowserSessionMock m : browsers) {
                final Pair<Integer, Integer> r = m.subjectRange;
                int i = r.getValue0().intValue();
                while (i != r.getValue1().intValue()) {
                    m.handler.onMessage(subMessages.get(i));
                    ++i;
                    if (i >= textMsgCount) {
                        i -= textMsgCount;
                    }
                }
            }

            for (int i = 0; i < textMsgCount; ++i) {
                ReplyMessage msgConf = confMessages.get(i);
                curvePublisherHandler.onMessage(msgConf);
            }

            int messagesPerSubject = 5;

            for (int k = 0; k < messagesPerSubject; ++k) {
                for (int i = 0; i < textMsgCount; ++i) {
                    ReplyMessage msgText = textMessages.get(i);
                    curvePublisherHandler.onMessage(msgText);
                }
            }

            // Unsubscribing browsers from all subjects except one
            for (BrowserSessionMock m : browsers) {
                final Pair<Integer, Integer> r = m.subjectRange;
                int i = r.getValue0().intValue() + 1;
                if (i >= textMsgCount) {
                    i -= textMsgCount;
                }
                while (i != r.getValue1().intValue()) {
                    m.handler.onMessage(unsubMessages.get(i));
                    ++i;
                    if (i >= textMsgCount) {
                        i -= textMsgCount;
                    }
                }
            }

            // Unsubscribing all
            for (BrowserSessionMock m : browsers) {
                final Pair<Integer, Integer> r = m.subjectRange;
                int i = r.getValue0().intValue();
                while (i != r.getValue1().intValue()) {
                    m.handler.onMessage(unsubMessages.get(i));
                    ++i;
                    if (i >= textMsgCount) {
                        i -= textMsgCount;
                    }
                }
            }
        }
    }

    private class BrowserSessionMock {
        public int id;
        public Session session;
        public RequestMessageHandler handler;
        public Async endpoint;
        public Pair<Integer, Integer> subjectRange;
    }
}
