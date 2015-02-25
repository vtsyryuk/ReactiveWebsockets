package wsx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import rx.Observable;
import rx.functions.Func1;

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

        return replyStream.filter(new Func1<ReplyMessage, Boolean>() {
            @Override
            public Boolean call(ReplyMessage msg) {
                // INFO: filtering incoming messages by subject
                return msg.getSubject().equals(srcSubject);
            }
        }).startWith(msgConfirmation);
    }
}