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

/**
 * Routes reply messages by subject and emits subscription requests for new streams.
 */
@Component
public class SubscriptionRouter implements DataSource {

    private final Subject<RequestMessage> requestStream;
    private final Observable<ReplyMessage> replyStream;
    private final Map<MessageSubject, Observable<ReplyMessage>> replyStreams;

    /**
     * Creates a router over a shared reply stream.
     *
     * @param replyStream stream of all reply messages received from the websocket
     */
    @Autowired
    public SubscriptionRouter(Observable<ReplyMessage> replyStream) {
        this.requestStream = PublishSubject.<RequestMessage>create().toSerialized();
        this.replyStreams = new ConcurrentHashMap<>();
        this.replyStream = replyStream;
    }

    /**
     * Returns a cached stream for the supplied subject, creating one on first use.
     *
     * @param subject subject to subscribe to
     * @return reply stream for the subject
     */
    @Override
    public Observable<ReplyMessage> getDataStream(final MessageSubject subject) {
        return replyStreams.computeIfAbsent(subject, this::createStream);
    }

    /**
     * Returns the outbound request stream produced by subject subscriptions.
     *
     * @return request stream
     */
    public Observable<RequestMessage> getRequestStream() {
        return requestStream.hide();
    }

    /**
     * Re-emits subscribe requests for all currently tracked subjects.
     */
    public void reSendSubscribeRequests() {
        for (Entry<MessageSubject, Observable<ReplyMessage>> item : replyStreams.entrySet()) {
            generateRequest(RequestMessageType.SUBSCRIBE, item.getKey());
        }
    }

    private Observable<ReplyMessage> createStream(final MessageSubject subject) {
        final Observable<ReplyMessage> subjectStream = replyStream.filter(msg -> msg.getSubject().equals(subject));

        // Replay the first reply as a subscription confirmation for each local subscriber.
        final ConnectableObservable<ReplyMessage> confirmationStream = subjectStream.take(1).replay();
        // Share later replies while at least one local subscriber remains active.
        final Observable<ReplyMessage> dataStream = subjectStream.skip(1).publish().refCount();

        final Observable<ReplyMessage> requestGenerator = Observable.<ReplyMessage>create(emitter -> {
            final Disposable connection = confirmationStream.connect();
            generateRequest(RequestMessageType.SUBSCRIBE, subject);
            emitter.setCancellable(() -> {
                generateRequest(RequestMessageType.UNSUBSCRIBE, subject);
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
