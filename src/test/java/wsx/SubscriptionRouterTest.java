package wsx;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

final class SubscriptionRouterTest {

    private SubscriptionRouter router;
    private PublishSubject<ReplyMessage> textStream;
    private List<RequestMessage> requests;

    @BeforeEach
    void setUp() {
        textStream = PublishSubject.create();
        router = new SubscriptionRouter(textStream);
        requests = new ArrayList<>();
        router.getRequestStream().subscribe(requests::add);
    }

    @Test
    void subscriptionTest() {
        final List<ReplyMessage> txtMsgs = new ArrayList<>();
        MessageSubject key = MessageSubjectFactory.create("Subject", "Subject1");
        Disposable s1 = router.getDataStream(key).subscribe(txtMsgs::add);
        assertEquals(1, requests.size());
        assertSame(RequestMessageType.SUBSCRIBE, requests.get(0).getContent());
        assertSame(key, requests.get(0).getSubject());
        String content = "Content1";
        ReplyMessage txtMsg1 = ReplyMessage.create(key, content);
        textStream.onNext(txtMsg1);
        assertEquals(1, txtMsgs.size());

        s1.dispose();
        assertEquals(2, requests.size());
        assertSame(RequestMessageType.UNSUBSCRIBE, requests.get(1).getContent());
        assertSame(key, requests.get(1).getSubject());
        textStream.onNext(txtMsg1);
        assertEquals(1, txtMsgs.size());
    }

    @Test
    void multipleSubscriptionsTest() {
        final List<ReplyMessage> txtMsgs1 = new ArrayList<>();
        final List<ReplyMessage> txtMsgs2 = new ArrayList<>();
        MessageSubject key = MessageSubjectFactory.create("Subject", "Subject1");
        Disposable s1 = router.getDataStream(key).subscribe(txtMsgs1::add);
        Disposable s2 = router.getDataStream(key).subscribe(txtMsgs2::add);
        assertEquals(1, requests.size());
        assertSame(RequestMessageType.SUBSCRIBE, requests.get(0).getContent());
        assertSame(key, requests.get(0).getSubject());
        String content = "Content1";
        ReplyMessage txtMsg1 = ReplyMessage.create(key, content);
        textStream.onNext(txtMsg1);
        assertEquals(1, txtMsgs1.size());
        assertEquals(1, txtMsgs2.size());
        textStream.onNext(txtMsg1);
        assertEquals(2, txtMsgs1.size());
        assertEquals(2, txtMsgs2.size());
        s1.dispose();
        assertEquals(1, requests.size());
        textStream.onNext(txtMsg1);
        assertEquals(2, txtMsgs1.size());
        assertEquals(3, txtMsgs2.size());
        s2.dispose();
        assertEquals(2, requests.size());
        assertSame(RequestMessageType.UNSUBSCRIBE, requests.get(1).getContent());
        assertSame(key, requests.get(1).getSubject());
    }

    @Test
    void concurrentGetDataStreamReturnsSingleSharedStream() throws InterruptedException {
        MessageSubject key = MessageSubjectFactory.create("Subject", "Subject1");
        Set<Integer> streamIdentities = ConcurrentHashMap.newKeySet();
        ExecutorService executor = Executors.newFixedThreadPool(8);

        for (int i = 0; i < 100; i++) {
            executor.submit(() -> streamIdentities.add(System.identityHashCode(router.getDataStream(key))));
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(1, streamIdentities.size());
    }

    @Test
    void resendSubscribeRequestsEmitsCurrentSubjects() {
        MessageSubject first = MessageSubjectFactory.create("Subject", "Subject1");
        MessageSubject second = MessageSubjectFactory.create("Subject", "Subject2");

        Disposable firstSubscription = router.getDataStream(first).subscribe();
        Disposable secondSubscription = router.getDataStream(second).subscribe();
        requests.clear();

        router.reSendSubscribeRequests();

        assertEquals(2, requests.size());
        assertTrue(requests.stream().allMatch(request -> request.getContent() == RequestMessageType.SUBSCRIBE));
        assertTrue(requests.stream().map(RequestMessage::getSubject).anyMatch(first::equals));
        assertTrue(requests.stream().map(RequestMessage::getSubject).anyMatch(second::equals));

        firstSubscription.dispose();
        secondSubscription.dispose();
    }
}
