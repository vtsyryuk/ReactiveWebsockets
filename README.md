# ReactiveWebsockets

[![CI](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/ci.yml/badge.svg)](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/ci.yml)
[![GitHub Actions](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/actions.yml/badge.svg)](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/actions.yml)
[![CodeQL](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/codeql.yml/badge.svg)](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/codeql.yml)
[![Cloud E2E](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/cloud-e2e.yml/badge.svg)](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/cloud-e2e.yml)
[![SonarCloud](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/sonarcloud.yml/badge.svg)](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/sonarcloud.yml)
[![Dependency Review](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/dependency-review.yml/badge.svg)](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/dependency-review.yml)
[![Dependency Submission](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/dependency-submission.yml/badge.svg)](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/dependency-submission.yml)
[![Publish](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/publish.yml/badge.svg)](https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/publish.yml)

ReactiveWebsockets is a small Java library for routing websocket subscribe/unsubscribe requests to RxJava streams and publishing replies back to websocket clients.

<!-- ci-status:start -->
## CI Status

| Build | Line Coverage | Branch Coverage | Instruction Coverage | Workflow Run |
| --- | ---: | ---: | ---: | --- |
| ✅ Passing | 99.07% | 85.29% | 98.65% | [#29](https://github.com/vtsyryuk/ReactiveWebsockets/actions/runs/28638762119) |

Last updated from `master` at 2026-07-03 04:38 UTC for commit `a55cd44`.
<!-- ci-status:end -->

## Project Links

- Live demo: https://reactivewebsockets.onrender.com
- Render blueprint: `render.yaml`
- Cloud E2E workflow: https://github.com/vtsyryuk/ReactiveWebsockets/actions/workflows/cloud-e2e.yml
- Latest verified cloud run: https://github.com/vtsyryuk/ReactiveWebsockets/actions/runs/28637845856
- SonarCloud summary: https://sonarcloud.io/project/overview?id=vtsyryuk_ReactiveWebsockets

## Requirements

- Java 25
- Gradle Wrapper 9.6.1
- RxJava 3
- JUnit 6 and Mockito 5 for tests

## Build

```bash
./gradlew clean check
```

The CI workflow builds the library, runs tests, generates JaCoCo XML/HTML coverage, enforces coverage verification, uploads build artifacts, and publishes a Gradle build scan. CodeQL, SonarCloud, Dependency Review, Dependabot, Gradle dependency submission, cloud UI E2E tests, and GitHub Actions workflow linting are enabled for quality, supply-chain, deployment, and workflow scanning.

## Test and Coverage

```bash
./gradlew test jacocoTestReport
```

Coverage reports are written to:

- HTML: `build/reports/jacoco/test/html/index.html`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`

Gradle is configured to use a Java 25 toolchain. If Java 25 is not available locally, Gradle can download a matching toolchain through the Foojay resolver.

## SonarCloud

SonarCloud analysis runs from the `SonarCloud Analysis` workflow on pushes, pull requests, and manual dispatch. The workflow builds the Java test and JaCoCo XML reports before invoking the Sonar Gradle scanner.

Required GitHub Actions configuration:

- secret `SONAR_TOKEN`: generated from SonarCloud
- variable `SONAR_ORGANIZATION`: the SonarCloud organization key
- variable `SONAR_PROJECT_KEY`: `vtsyryuk_ReactiveWebsockets`

The workflow reports coverage from `build/reports/jacoco/test/jacocoTestReport.xml`. The demo HTTP service is excluded from Sonar coverage to match the JaCoCo verification scope for the reusable library. Until the secret and both variables exist, the workflow builds the reports and exits successfully with a notice instead of failing the PR.

## Publish

Packages are deployed to GitHub Packages by the `Publish` workflow when a GitHub release is created, or when the workflow is run manually.

```bash
./gradlew publish -PreleaseVersion=2.0.0
```

To run the same publish path locally, provide GitHub Packages credentials through `gpr.user`/`gpr.key` Gradle properties or `GITHUB_ACTOR`/`GITHUB_TOKEN` environment variables.

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

## Demo Deployment

The library includes a small HTTP demo service with a browser UI and JSON API. It demonstrates:

- browser clients subscribing to fake websocket topics
- `SubscriptionRouter` emitting upstream subscribe/unsubscribe commands
- publishing replies through `ReplyMessageService`
- multiple clients sharing one upstream topic in a simulation

The hosted demo is deployed on Render at https://reactivewebsockets.onrender.com.

Run it locally:

```bash
./gradlew run
```

Then try:

```bash
open http://localhost:8080
curl http://localhost:8080/api/topics
curl -X POST 'http://localhost:8080/api/subscribe?client=alice&topic=prices'
curl -X POST 'http://localhost:8080/api/publish?topic=prices&content=price=42.10'
curl -X POST 'http://localhost:8080/api/simulate?topic=alerts'
```

### Render Free Cloud Demo

The repository includes `Dockerfile` and `render.yaml` for deploying the demo as a Render Free web service. In Render, create a new Blueprint from this repository. The service starts the Java demo container, exposes `/health`, serves the browser UI from `/`, and keeps demo subscriptions in memory.

Render Free web services are suitable for demos and hobby projects, but they can spin down after idle time and their local filesystem is ephemeral. Do not use the demo deployment as production storage or coordination infrastructure.

### Cloud UI E2E

The `Cloud E2E` workflow runs Playwright browser tests against the deployed Render demo. It can run after successful deployment status events, runs daily to keep the cloud demo and status badge fresh, and can also be run manually from GitHub Actions with an optional `base_url` override.

The current Render demo URL was verified by the `Cloud E2E` workflow in run [#28637845856](https://github.com/vtsyryuk/ReactiveWebsockets/actions/runs/28637845856).

Run the same tests locally against any deployed demo:

```bash
npm ci --ignore-scripts
PLAYWRIGHT_BASE_URL=https://reactivewebsockets.onrender.com npm run test:e2e
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
