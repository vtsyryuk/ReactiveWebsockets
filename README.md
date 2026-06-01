# ReactiveWebsockets

ReactiveWebsockets is a small Java library for routing websocket subscribe/unsubscribe requests to RxJava streams and publishing replies back to websocket clients.

<!-- ci-status:start -->
## CI Status

| Build | Line Coverage | Branch Coverage | Instruction Coverage | Workflow Run |
| --- | ---: | ---: | ---: | --- |
| ✅ Passing | 99.10% | 85.71% | 98.76% | [#6](https://github.com/vtsyryuk/ReactiveWebsockets/actions/runs/26786760358) |

Last updated from `master` at 2026-06-01 22:49 UTC for commit `640843a`.
<!-- ci-status:end -->

## Requirements

- Java 25
- Gradle 9.4+
- RxJava 3

## Build

```bash
./gradlew build
```

The GitHub Actions CI workflow runs the same Gradle build on pushes and pull requests to `main` and `master`.

## Test and Coverage

```bash
./gradlew test
./gradlew jacocoTestReport
```

Coverage reports are written to:

- HTML: `build/reports/jacoco/test/html/index.html`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`

Gradle is configured to use a Java 25 toolchain. If Java 25 is not available locally, Gradle can download a matching toolchain through the Foojay resolver.

## VS Code

Open the repository folder in VS Code and install the recommended extensions when prompted. The workspace includes tasks for:

- `Gradle: build`
- `Gradle: test`
- `Gradle: coverage`

If VS Code reports `there is no registered task type 'Java'`, install the recommended Java extensions and reload the window. This workspace only defines shell-based Gradle tasks, so a `Java` task usually comes from a stale user task or an extension-provided task that is unavailable.

To clear it:

1. Run `Extensions: Show Recommended Extensions` and install the recommendations.
2. Run `Developer: Reload Window`.
3. Run `Tasks: Run Task` and choose `Gradle: test`.
4. If the error remains, run `Tasks: Open User Tasks` and remove any task whose `"type"` is `"Java"`.

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
