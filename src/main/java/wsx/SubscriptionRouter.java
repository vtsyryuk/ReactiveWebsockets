package wsx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observables.ConnectableObservable;
import io.reactivex.rxjava3.subjects.Subject;
import io.reactivex.rxjava3.subjects.PublishSubject;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SubscriptionRouter implements DataSource {

    private final Subject<RequestMessage> requestStream;
    private final Observable<ReplyMessage> replyStream;
    private final Map<MessageSubject, Observable<ReplyMessage>> replyStreams;

    @Autowired
    public SubscriptionRouter(Observable<ReplyMessage> replyStream) {
        this.requestStream = PublishSubject.<RequestMessage>create().toSerialized();
        this.replyStreams = new ConcurrentHashMap<>();
        this.replyStream = replyStream;
    }

    @Override
    public Observable<ReplyMessage> getDataStream(final MessageSubject subject) {
        return replyStreams.computeIfAbsent(subject, this::createStream);
    }

    public Observable<RequestMessage> getRequestStream() {
        return requestStream.hide();
    }

    public void reSendSubscribeRequests() {
        for (Entry<MessageSubject, Observable<ReplyMessage>> item : replyStreams.entrySet()) {
            generateRequest(RequestMessageType.Subscribe, item.getKey());
        }
    }

    private Observable<ReplyMessage> createStream(final MessageSubject subject) {
        final Observable<ReplyMessage> subjectStream = replyStream.filter(msg -> msg.getSubject().equals(subject));

        // INFO: caching message confirmation so that each new subscriber gets it
        final ConnectableObservable<ReplyMessage> confirmationStream = subjectStream.take(1).replay();
        // INFO: filtering data stream out by skipping message confirmation
        final Observable<ReplyMessage> dataStream = subjectStream.skip(1).publish().refCount();

        final Observable<ReplyMessage> requestGenerator = Observable.<ReplyMessage>create(emitter -> {
            final Disposable connection = confirmationStream.connect();
            generateRequest(RequestMessageType.Subscribe, subject);
            emitter.setCancellable(() -> {
                generateRequest(RequestMessageType.Unsubscribe, subject);
                connection.dispose();
                replyStreams.remove(subject);
            });
        }).publish().refCount();

        return Observable.merge(confirmationStream, dataStream, requestGenerator);
    }

    private void generateRequest(final RequestMessageType command, final MessageSubject subject) {
        final RequestMessage reqMsg = RequestMessage.create(subject, command);
        requestStream.onNext(reqMsg);
    }
}
