package wsx;

import io.reactivex.rxjava3.disposables.Disposable;

import javax.websocket.Session;

/**
 * Attaches websocket sessions to the library message handling pipeline.
 */
public interface SessionManager {

    /**
     * Attaches handlers to the session and returns a disposable that detaches them.
     *
     * @param session websocket session to manage
     * @return disposable cleanup handle
     */
    Disposable attach(Session session);

}
