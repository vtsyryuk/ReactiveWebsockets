package wsx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.reactivex.rxjava3.core.Observable;


@Component
public class PassthroughDataSource implements DataSource {

    private static final String Subscribed = "Subscribed";
    private final Observable<ReplyMessage> replyStream;

    @Autowired
    public PassthroughDataSource(Observable<ReplyMessage> replyStream) {
        this.replyStream = replyStream;
    }

    @Override
    public Observable<ReplyMessage> getDataStream(MessageSubject subject) {
        final MessageSubject srcSubject = subject;
        final ReplyMessage msgConfirmation = ReplyMessage.create(srcSubject, PassthroughDataSource.Subscribed);

        return replyStream
                .filter(msg -> msg.getSubject().equals(srcSubject))
                .startWithItem(msgConfirmation);
    }
}
