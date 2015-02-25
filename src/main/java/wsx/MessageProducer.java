package wsx;

import rx.Observer;

public interface MessageProducer<T> {

    public Observer<T> getPublisher();

}