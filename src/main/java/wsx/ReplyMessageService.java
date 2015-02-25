package wsx;

import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;

@Component
public final class ReplyMessageService implements MessageConsumer<ReplyMessage>, MessageProducer<ReplyMessage> {

    private final PublishSubject<ReplyMessage> subject = PublishSubject.create();

    @Override
    public Observer<ReplyMessage> getPublisher() {
        return subject;
    }

    @Override
    public Observable<ReplyMessage> getStream() {
        return subject.asObservable();
    }
}
