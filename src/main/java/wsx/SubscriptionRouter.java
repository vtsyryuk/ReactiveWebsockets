package wsx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.subjects.PublishSubject;
import rx.subscriptions.Subscriptions;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SubscriptionRouter implements DataSource {

    private final PublishSubject<RequestMessage> requestStream;
    private final Observable<ReplyMessage> replyStream;
    private final Map<MessageSubject, Observable<ReplyMessage>> replyStreams;

    @Autowired
    public SubscriptionRouter(Observable<ReplyMessage> replyStream) {
        this.requestStream = PublishSubject.create();
        this.replyStreams = new ConcurrentHashMap<>();
        this.replyStream = replyStream;
    }

    @Override
    public Observable<ReplyMessage> getDataStream(final MessageSubject subject) {
        Observable<ReplyMessage> stream = replyStreams.get(subject);
        if (stream == null) {
            stream = createStream(subject);
            replyStreams.put(subject, stream);
        }
        return stream;
    }

    public Observable<RequestMessage> getRequestStream() {
        return requestStream.asObservable();
    }

    public void reSendSubscribeRequests() {
        for (Entry<MessageSubject, Observable<ReplyMessage>> item : replyStreams.entrySet()) {
            generateRequest(RequestMessageType.Subscribe, item.getKey());
        }
    }

    private Observable<ReplyMessage> createStream(final MessageSubject subject) {
        final Observable<ReplyMessage> subjectStream = replyStream.filter(new Func1<ReplyMessage, Boolean>() {
            @Override
            public Boolean call(ReplyMessage msg) {
                // INFO: filtering incoming messages by subject
                return msg.getSubject().equals(subject);
            }
        });

        // INFO: caching message confirmation so that each new subscriber gets it
        final ConnectableObservable<ReplyMessage> confirmationStream = subjectStream.take(1).replay();
        // INFO: filtering data stream out by skipping message confirmation
        final Observable<ReplyMessage> dataStream = subjectStream.skip(1).publish().refCount();

        final Observable<ReplyMessage> requestGenerator = Observable.create(new OnSubscribe<ReplyMessage>() {
            @Override
            public void call(Subscriber<? super ReplyMessage> subscriber) {
                final Subscription s = confirmationStream.connect();
                generateRequest(RequestMessageType.Subscribe, subject);
                subscriber.add(Subscriptions.create(new Action0() {
                    @SuppressWarnings("synthetic-access")
                    @Override
                    public void call() {
                        generateRequest(RequestMessageType.Unsubscribe, subject);
                        s.unsubscribe();
                        replyStreams.remove(subject);
                    }
                }));
            }
        }).publish().refCount();

        return Observable.create(new OnSubscribe<ReplyMessage>() {
            @Override
            public void call(final Subscriber<? super ReplyMessage> subscriber) {
                Observable.merge(confirmationStream, dataStream, requestGenerator)
                        .subscribe(subscriber);
            }
        });
    }

    private void generateRequest(final RequestMessageType command, final MessageSubject subject) {
        final RequestMessage reqMsg = RequestMessage.create(subject, command);
        requestStream.onNext(reqMsg);
    }
}