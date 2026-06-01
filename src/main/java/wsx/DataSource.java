package wsx;

import io.reactivex.rxjava3.core.Observable;

public interface DataSource {
    public Observable<ReplyMessage> getDataStream(final MessageSubject subject);
}