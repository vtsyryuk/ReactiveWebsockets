package wsx;

import io.reactivex.rxjava3.core.Observable;

public interface MessageConsumer<T> {

    public Observable<T> getStream();

}