package wsx;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("synthetic-access")
public final class SubscriptionRouterTest {

    private SubscriptionRouter router;
    private PublishSubject<ReplyMessage> textStream;
    private List<RequestMessage> requests;

    @Before
    public void setUp() {
        textStream = PublishSubject.create();
        router = new SubscriptionRouter(textStream);
        requests = new ArrayList<>();
        router.getRequestStream().subscribe(new Action1<RequestMessage>() {
            @Override
            public void call(RequestMessage t1) {
                requests.add(t1);
            }
        });
    }

    @Test
    public void subscriptionTest() {
        final List<ReplyMessage> txtMsgs = new ArrayList<>();
        MessageSubject key = MessageSubjectFactory.create("Subject", "Subject1");
        rx.Subscription s1 = router.getDataStream(key).subscribe(new Action1<ReplyMessage>() {
            @Override
            public void call(ReplyMessage t1) {
                txtMsgs.add(t1);
            }
        });
        Assert.assertEquals(1, requests.size());
        Assert.assertTrue(requests.get(0).getContent() == RequestMessageType.Subscribe);
        Assert.assertTrue(requests.get(0).getSubject() == key);
        String content = "Content1";
        ReplyMessage txtMsg1 = ReplyMessage.create(key, content);
        textStream.onNext(txtMsg1);
        Assert.assertEquals(1, txtMsgs.size());

        s1.unsubscribe();
        Assert.assertEquals(2, requests.size());
        Assert.assertTrue(requests.get(1).getContent() == RequestMessageType.Unsubscribe);
        Assert.assertTrue(requests.get(1).getSubject() == key);
        textStream.onNext(txtMsg1);
        Assert.assertEquals(1, txtMsgs.size());
    }

    @Test
    public void multipleSubscriptionsTest() {
        final List<ReplyMessage> txtMsgs1 = new ArrayList<>();
        final List<ReplyMessage> txtMsgs2 = new ArrayList<>();
        MessageSubject key = MessageSubjectFactory.create("Subject", "Subject1");
        rx.Subscription s1 = router.getDataStream(key).subscribe(new Action1<ReplyMessage>() {
            @Override
            public void call(ReplyMessage t1) {
                txtMsgs1.add(t1);
            }
        });
        rx.Subscription s2 = router.getDataStream(key).subscribe(new Action1<ReplyMessage>() {
            @Override
            public void call(ReplyMessage t1) {
                txtMsgs2.add(t1);
            }
        });
        Assert.assertEquals(1, requests.size());
        Assert.assertTrue(requests.get(0).getContent() == RequestMessageType.Subscribe);
        Assert.assertTrue(requests.get(0).getSubject() == key);
        String content = "Content1";
        ReplyMessage txtMsg1 = ReplyMessage.create(key, content);
        textStream.onNext(txtMsg1);
        Assert.assertEquals(1, txtMsgs1.size());
        Assert.assertEquals(1, txtMsgs2.size());
        textStream.onNext(txtMsg1);
        Assert.assertEquals(2, txtMsgs1.size());
        Assert.assertEquals(2, txtMsgs2.size());
        s1.unsubscribe();
        Assert.assertEquals(1, requests.size());
        textStream.onNext(txtMsg1);
        Assert.assertEquals(2, txtMsgs1.size());
        Assert.assertEquals(3, txtMsgs2.size());
        s2.unsubscribe();
        Assert.assertEquals(2, requests.size());
        Assert.assertTrue(requests.get(1).getContent() == RequestMessageType.Unsubscribe);
        Assert.assertTrue(requests.get(1).getSubject() == key);
    }
}
