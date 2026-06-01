# ReactiveWebsockets

ReactiveWebsockets is a small Java library for routing websocket subscribe/unsubscribe requests to RxJava streams and publishing replies back to websocket clients.

## Requirements

- Java 25
- Gradle 9.4+
- RxJava 3

## Build

```bash
./gradlew build
```

The GitHub Actions CI workflow runs the same Gradle build on pushes and pull requests to `main` and `master`.

## Publish

Packages are deployed to GitHub Packages by `.github/workflows/deploy.yml` when a GitHub release is published, or when the workflow is run manually.

The deployment workflow uses:

- `actions/setup-java@v4`
- Temurin Java 25
- Gradle `publish`
- `GITHUB_TOKEN` with `packages: write`

## Basic Usage

```java
ReplyMessageService replies = new ReplyMessageService();
SubscriptionRouter router = new SubscriptionRouter(replies.getStream());

router.getRequestStream().subscribe(request -> {
    // Forward subscribe/unsubscribe commands to your upstream websocket.
});

MessageSubject subject = MessageSubject.of("topic", "prices");
router.getDataStream(subject).subscribe(reply -> {
    // Handle replies for this subject.
});
```

## Message Flow

1. A client sends a `RequestMessage` with `Subscribe` or `Unsubscribe`.
2. `RequestMessageHandler` opens or closes the subject subscription.
3. `SubscriptionRouter` emits upstream request messages only once per subject while one or more local subscribers exist.
4. `ReplyMessageHandler` publishes upstream replies into `ReplyMessageService`.
5. Matching `ReplyMessage` values are sent back to subscribed websocket sessions.

## Notes

- Internal RxJava subjects are serialized for safe concurrent websocket callbacks.
- Reconnection retry timers are disposed when `AutoReconnection.close()` is called.
- Timestamps use `java.time.Instant` and serialize as ISO-8601 strings.
