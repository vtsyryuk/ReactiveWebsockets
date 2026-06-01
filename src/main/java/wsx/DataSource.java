package wsx;

import io.reactivex.rxjava3.core.Observable;

/**
 * Provides reply streams for websocket subscription subjects.
 */
public interface DataSource {

    /**
     * Returns an observable stream of reply messages for the requested subject.
     *
     * @param subject subject to subscribe to
     * @return stream of reply messages associated with the subject
     */
    public Observable<ReplyMessage> getDataStream(final MessageSubject subject);
}
