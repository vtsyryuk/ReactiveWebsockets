package wsx;

import org.springframework.stereotype.Component;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * In-memory reply message bus backed by a serialized RxJava subject.
 */
@Component
public final class ReplyMessageService implements MessageConsumer<ReplyMessage>, MessageProducer<ReplyMessage> {

    private final Subject<ReplyMessage> subject = PublishSubject.<ReplyMessage>create().toSerialized();

    /**
     * Returns the observer used to publish reply messages.
     *
     * @return reply publisher
     */
    @Override
    public Observer<ReplyMessage> getPublisher() {
        return subject;
    }

    /**
     * Returns the read-only reply message stream.
     *
     * @return reply stream
     */
    @Override
    public Observable<ReplyMessage> getStream() {
        return subject.hide();
    }
}
