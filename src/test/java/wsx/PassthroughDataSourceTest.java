package wsx;

import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subjects.PublishSubject;
import org.junit.jupiter.api.Test;

public final class PassthroughDataSourceTest {

    @Test
    public void dataStreamStartsWithConfirmationAndFiltersBySubject() {
        PublishSubject<ReplyMessage> upstream = PublishSubject.create();
        PassthroughDataSource dataSource = new PassthroughDataSource(upstream);
        MessageSubject subject = MessageSubject.of("topic", "prices");
        MessageSubject other = MessageSubject.of("topic", "news");

        TestObserver<ReplyMessage> observer = dataSource.getDataStream(subject).test();
        ReplyMessage matching = ReplyMessage.create(subject, "42");

        upstream.onNext(ReplyMessage.create(other, "ignore"));
        upstream.onNext(matching);

        observer.assertValueAt(0, message -> message.getSubject().equals(subject)
                && "Subscribed".equals(message.getContent()));
        observer.assertValueAt(1, matching);
        observer.assertValueCount(2);
    }
}
