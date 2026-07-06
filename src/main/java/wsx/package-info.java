/**
 * Reactive websocket subscription routing primitives.
 *
 * <p>The package is built around two message directions:</p>
 *
 * <ul>
 *     <li>{@link wsx.RequestMessage} values represent downstream subscribe and unsubscribe commands.</li>
 *     <li>{@link wsx.ReplyMessage} values represent upstream replies routed back to matching subscribers.</li>
 * </ul>
 *
 * <p>{@link wsx.SubscriptionRouter} keeps the subject-level coordination point. It exposes reply streams per
 * {@link wsx.MessageSubject} while emitting upstream request messages only when local subscriber demand changes.
 * Endpoint and session-manager classes adapt those streams to the Java websocket API, and RxJava serialized subjects
 * keep callbacks safe when containers invoke websocket lifecycle methods concurrently.</p>
 */
package wsx;
