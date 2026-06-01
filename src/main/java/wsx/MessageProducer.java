package wsx;

import io.reactivex.rxjava3.core.Observer;

public interface MessageProducer<T> {

    public Observer<T> getPublisher();

}