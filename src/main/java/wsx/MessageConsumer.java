package wsx;

import io.reactivex.rxjava3.core.Observable;

/**
 * Exposes an observable stream of messages of a particular type.
 *
 * @param <T> message type emitted by the stream
 */
public interface MessageConsumer<T> {

    /**
     * Returns the read-only message stream.
     *
     * @return message stream
     */
    public Observable<T> getStream();

}
