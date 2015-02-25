package wsx;

import rx.Observable;

public interface DataSource {
    public Observable<ReplyMessage> getDataStream(final MessageSubject subject);
}