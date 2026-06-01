package wsx;

import org.springframework.stereotype.Component;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

@Component
public final class ReplyMessageService implements MessageConsumer<ReplyMessage>, MessageProducer<ReplyMessage> {

    private final Subject<ReplyMessage> subject = PublishSubject.<ReplyMessage>create().toSerialized();

    @Override
    public Observer<ReplyMessage> getPublisher() {
        return subject;
    }

    @Override
    public Observable<ReplyMessage> getStream() {
        return subject.hide();
    }
}
