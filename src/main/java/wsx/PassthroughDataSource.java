package wsx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.reactivex.rxjava3.core.Observable;

/**
 * Data source that exposes a filtered view of an existing reply stream.
 */
@Component
public class PassthroughDataSource implements DataSource {

    private static final String SUBSCRIBED = "Subscribed";
    private final Observable<ReplyMessage> replyStream;

    /**
     * Creates a pass-through data source over the supplied reply stream.
     *
     * @param replyStream stream of all reply messages
     */
    @Autowired
    public PassthroughDataSource(Observable<ReplyMessage> replyStream) {
        this.replyStream = replyStream;
    }

    /**
     * Returns reply messages matching the subject and starts with a subscription confirmation.
     *
     * @param subject subject to filter by
     * @return filtered reply stream
     */
    @Override
    public Observable<ReplyMessage> getDataStream(MessageSubject subject) {
        final MessageSubject srcSubject = subject;
        final ReplyMessage msgConfirmation = ReplyMessage.create(srcSubject, PassthroughDataSource.SUBSCRIBED);

        return replyStream
                .filter(msg -> msg.getSubject().equals(srcSubject))
                .startWithItem(msgConfirmation);
    }
}
