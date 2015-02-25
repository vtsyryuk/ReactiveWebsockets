package wsx;

import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;

@Component
public final class DiagnosticMessageService implements MessageConsumer<DiagnosticMessage>, MessageProducer<DiagnosticMessage> {

    private final PublishSubject<DiagnosticMessage> subject = PublishSubject.create();

    @Override
    public Observer<DiagnosticMessage> getPublisher() {
        return subject;
    }

    @Override
    public Observable<DiagnosticMessage> getStream() {
        return subject.asObservable();
    }
}
