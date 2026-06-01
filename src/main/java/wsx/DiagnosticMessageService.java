package wsx;

import org.springframework.stereotype.Component;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

@Component
public final class DiagnosticMessageService implements MessageConsumer<DiagnosticMessage>, MessageProducer<DiagnosticMessage> {

    private final Subject<DiagnosticMessage> subject = PublishSubject.<DiagnosticMessage>create().toSerialized();

    @Override
    public Observer<DiagnosticMessage> getPublisher() {
        return subject;
    }

    @Override
    public Observable<DiagnosticMessage> getStream() {
        return subject.hide();
    }
}
