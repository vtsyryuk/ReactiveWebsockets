package wsx;

import rx.Subscription;

import javax.websocket.Session;

public interface SessionManager {

    Subscription attach(Session session);

}
