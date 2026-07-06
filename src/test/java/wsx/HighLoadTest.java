package wsx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;

import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.RemoteEndpoint.Async;
import javax.websocket.SendHandler;
import javax.websocket.Session;
import java.util.ArrayList;
import java.util.List;

final class HighLoadTest {

    private SocketEndpoint webappClient;
    private SocketEndpoint webappServer;
    private ReplyMessageHandler webappHandler;
    private Session webappSession;
    private Async webappEndpoint;
    private int browserCount;
    private List<BrowserSessionMock> browsers;
    private int textMsgCount;
    private List<ReplyMessage> textMessages;
    private List<ReplyMessage> confMessages;
    private List<RequestMessage> subMessages;
    private List<RequestMessage> unsubMessages;

    @BeforeEach
    void setUp() {
        Scheduler scheduler = Schedulers.trampoline();
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

        browserCount = 100;
        browsers = new ArrayList<>(browserCount);
        int subjectsPerBrowser = 10;

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
            b.subjectRange = new Range(x, y);

            browsers.add(b);
        }

        webappSession = Mockito.mock(Session.class);
        webappEndpoint = Mockito.mock(Async.class);
        Mockito.when(webappSession.getAsyncRemote()).thenReturn(webappEndpoint);
        Mockito.when(webappSession.getId()).thenReturn(String.valueOf(browserCount + 1));

        Mockito.doAnswer(new Answer<ReplyMessageHandler>() {
            @Override
            public ReplyMessageHandler answer(InvocationOnMock invocation) throws Throwable {
                webappHandler = (ReplyMessageHandler) invocation.getArguments()[0];
                return null;
            }
        })
                .when(webappSession)
                .addMessageHandler(Mockito.any(MessageHandler.class));

        for (int i = 0; i < textMsgCount; ++i) {
            final MessageSubject subj = MessageSubjectFactory.create("Subject", "Subject" + i);
            ReplyMessage msg = ReplyMessage.create(subj, "Content" + i);
            textMessages.add(msg);

            ReplyMessage confMsg = ReplyMessage.create(subj, "Subscribed to " + subj);
            confMessages.add(confMsg);

            RequestMessage subMsg = RequestMessage.create(subj, RequestMessageType.SUBSCRIBE);
            subMessages.add(subMsg);

            RequestMessage unsubMsg = RequestMessage.create(subj, RequestMessageType.UNSUBSCRIBE);
            unsubMessages.add(unsubMsg);
        }
    }

    @Test
    void testHighLoadBasicFlow() {

        EndpointConfig endpointConfig = Mockito.mock(EndpointConfig.class);

        for (BrowserSessionMock m : browsers) {
            m.handler = null;
        }

        for (BrowserSessionMock m : browsers) {
            webappServer.onOpen(m.session, endpointConfig);
        }

        webappHandler = null;
        webappClient.onOpen(webappSession, endpointConfig);

        assertTrue(browsers.stream().allMatch(browser -> browser.handler != null));
        for (BrowserSessionMock m : browsers) {
            assertNotNull(m.handler);
        }

        assertNotNull(webappHandler);

        // Subscribing each browser to a range of subjects
        for (BrowserSessionMock m : browsers) {
            final Range r = m.subjectRange;
            int i = r.start();
            while (i != r.end()) {
                m.handler.onMessage(subMessages.get(i));
                ++i;
                if (i >= textMsgCount) {
                    i -= textMsgCount;
                }
            }
        }

        for (int i = 0; i < textMsgCount; i += textMsgCount / 10) {
            Mockito.verify(webappEndpoint, Mockito.times(1))
                    .sendObject(Mockito.refEq(subMessages.get(i), "timestamp"), Mockito.any(SendHandler.class));
        }

        for (int i = 0; i < textMsgCount; ++i) {
            ReplyMessage msgConf = confMessages.get(i);
            webappHandler.onMessage(msgConf);
        }

        int messagesPerSubject = 5;

        for (int k = 0; k < messagesPerSubject; ++k) {
            for (int i = 0; i < textMsgCount; ++i) {
                ReplyMessage msgText = textMessages.get(i);
                webappHandler.onMessage(msgText);
            }
        }

        for (int k = 0; k < browserCount; k += browserCount / 10) {
            BrowserSessionMock m = browsers.get(k);
            final Range r = m.subjectRange;
            int i = r.start();
            while (i != r.end()) {
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
            final Range r = m.subjectRange;
            int i = r.start() + 1;
            if (i >= textMsgCount) {
                i -= textMsgCount;
            }
            while (i != r.end()) {
                m.handler.onMessage(unsubMessages.get(i));
                ++i;
                if (i >= textMsgCount) {
                    i -= textMsgCount;
                }
            }
        }

        for (int i = 0; i < textMsgCount; i += textMsgCount / 10) {
            Mockito.verify(webappEndpoint, Mockito.never())
                    .sendObject(Mockito.refEq(unsubMessages.get(i)), Mockito.any(SendHandler.class));
        }

        // Unsubscribing all
        for (BrowserSessionMock m : browsers) {
            final Range r = m.subjectRange;
            int i = r.start();
            while (i != r.end()) {
                m.handler.onMessage(unsubMessages.get(i));
                ++i;
                if (i >= textMsgCount) {
                    i -= textMsgCount;
                }
            }
        }

        for (int i = 0; i < textMsgCount; i += textMsgCount / 10) {
            Mockito.verify(webappEndpoint, Mockito.times(1))
                    .sendObject(Mockito.refEq(unsubMessages.get(i), "timestamp"), Mockito.any(SendHandler.class));
        }
    }

    @Test
    void testMemory() {

        EndpointConfig endpointConfig = Mockito.mock(EndpointConfig.class);

        for (BrowserSessionMock m : browsers) {
            m.handler = null;
        }

        for (BrowserSessionMock m : browsers) {
            webappServer.onOpen(m.session, endpointConfig);
        }

        webappHandler = null;
        webappClient.onOpen(webappSession, endpointConfig);


        int iterationCount = 10;
        for (int j = 0; j < iterationCount; ++j) {
            // Subscribing each browser to a range of subjects
            for (BrowserSessionMock m : browsers) {
                final Range r = m.subjectRange;
                int i = r.start();
                while (i != r.end()) {
                    m.handler.onMessage(subMessages.get(i));
                    ++i;
                    if (i >= textMsgCount) {
                        i -= textMsgCount;
                    }
                }
            }

            for (int i = 0; i < textMsgCount; ++i) {
                ReplyMessage msgConf = confMessages.get(i);
                webappHandler.onMessage(msgConf);
            }

            int messagesPerSubject = 5;

            for (int k = 0; k < messagesPerSubject; ++k) {
                for (int i = 0; i < textMsgCount; ++i) {
                    ReplyMessage msgText = textMessages.get(i);
                    webappHandler.onMessage(msgText);
                }
            }

            // Unsubscribing browsers from all subjects except one
            for (BrowserSessionMock m : browsers) {
                final Range r = m.subjectRange;
                int i = r.start() + 1;
                if (i >= textMsgCount) {
                    i -= textMsgCount;
                }
                while (i != r.end()) {
                    m.handler.onMessage(unsubMessages.get(i));
                    ++i;
                    if (i >= textMsgCount) {
                        i -= textMsgCount;
                    }
                }
            }

            // Unsubscribing all
            for (BrowserSessionMock m : browsers) {
                final Range r = m.subjectRange;
                int i = r.start();
                while (i != r.end()) {
                    m.handler.onMessage(unsubMessages.get(i));
                    ++i;
                    if (i >= textMsgCount) {
                        i -= textMsgCount;
                    }
                }
            }
        }

        assertTrue(browsers.stream().allMatch(browser -> browser.handler != null));
    }

    private class BrowserSessionMock {
        public int id;
        public Session session;
        public RequestMessageHandler handler;
        public Async endpoint;
        public Range subjectRange;
    }

    private record Range(int start, int end) {
    }
}
