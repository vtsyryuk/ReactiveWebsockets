package wsx;

import io.reactivex.rxjava3.disposables.Disposable;

import javax.websocket.Session;

public interface SessionManager {

    Disposable attach(Session session);

}
