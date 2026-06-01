package wsx;

import io.reactivex.rxjava3.core.Observer;

/**
 * Exposes an observer that accepts messages of a particular type.
 *
 * @param <T> message type accepted by the publisher
 */
public interface MessageProducer<T> {

    /**
     * Returns the write-side observer for publishing messages.
     *
     * @return message publisher
     */
    public Observer<T> getPublisher();

}
