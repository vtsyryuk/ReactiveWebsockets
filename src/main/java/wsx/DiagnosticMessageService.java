package wsx;

import org.springframework.stereotype.Component;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

/**
 * In-memory diagnostic message bus backed by a serialized RxJava subject.
 */
@Component
public final class DiagnosticMessageService implements MessageConsumer<DiagnosticMessage>, MessageProducer<DiagnosticMessage> {

    private final Subject<DiagnosticMessage> subject = PublishSubject.<DiagnosticMessage>create().toSerialized();

    /**
     * Returns the observer used to publish diagnostic messages.
     *
     * @return diagnostic publisher
     */
    @Override
    public Observer<DiagnosticMessage> getPublisher() {
        return subject;
    }

    /**
     * Returns the read-only diagnostic message stream.
     *
     * @return diagnostic stream
     */
    @Override
    public Observable<DiagnosticMessage> getStream() {
        return subject.hide();
    }
}
