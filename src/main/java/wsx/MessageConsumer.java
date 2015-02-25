package wsx;

import rx.Observable;

public interface MessageConsumer<T> {

    public Observable<T> getStream();

}