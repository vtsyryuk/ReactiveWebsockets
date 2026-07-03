package wsx.demo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.reactivex.rxjava3.disposables.Disposable;
import wsx.MessageSubject;
import wsx.ReplyMessage;
import wsx.ReplyMessageService;
import wsx.RequestMessage;
import wsx.SubscriptionRouter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public final class ReactiveWebsocketsDemoServer {

    private static final int DEFAULT_PORT = 8080;
    private static final List<String> TOPICS = List.of("prices", "orders", "alerts", "inventory");

    private final ReplyMessageService replyMessageService = new ReplyMessageService();
    private final SubscriptionRouter router = new SubscriptionRouter(replyMessageService.getStream());
    private final Map<String, DemoSubscription> subscriptions = new ConcurrentHashMap<>();
    private final List<RequestMessage> upstreamRequests = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", String.valueOf(DEFAULT_PORT)));
        new ReactiveWebsocketsDemoServer().start(port);
    }

    private void start(int port) throws IOException {
        router.getRequestStream().subscribe(upstreamRequests::add);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.printf("ReactiveWebsockets demo listening on port %d%n", port);
    }

    private void handle(HttpExchange exchange) {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(method) && "/".equals(path)) {
                respondHtml(exchange, 200, ui());
            } else if ("GET".equals(method) && "/api".equals(path)) {
                respond(exchange, 200, routes());
            } else if ("GET".equals(method) && "/health".equals(path)) {
                respond(exchange, 200, "{\"status\":\"ok\"}");
            } else if ("GET".equals(method) && "/api/topics".equals(path)) {
                respond(exchange, 200, topics());
            } else if ("GET".equals(method) && "/api/state".equals(path)) {
                respond(exchange, 200, state());
            } else if ("POST".equals(method) && "/api/subscribe".equals(path)) {
                respond(exchange, 200, subscribe(exchange.getRequestURI()));
            } else if ("POST".equals(method) && "/api/publish".equals(path)) {
                respond(exchange, 200, publish(exchange.getRequestURI()));
            } else if ("POST".equals(method) && "/api/unsubscribe".equals(path)) {
                respond(exchange, 200, unsubscribe(exchange.getRequestURI()));
            } else if ("POST".equals(method) && "/api/simulate".equals(path)) {
                respond(exchange, 200, simulate(exchange.getRequestURI()));
            } else {
                respond(exchange, 404, "{\"error\":\"not_found\"}");
            }
        } catch (IllegalArgumentException ex) {
            respond(exchange, 400, "{\"error\":\"" + json(ex.getMessage()) + "\"}");
        } catch (Exception ex) {
            respond(exchange, 500, "{\"error\":\"" + json(ex.getMessage()) + "\"}");
        } finally {
            exchange.close();
        }
    }

    private String subscribe(URI uri) {
        Map<String, String> query = query(uri);
        String topic = required(query, "topic");
        String client = query.getOrDefault("client", "browser");
        MessageSubject subject = MessageSubject.of("topic", topic);
        String id = UUID.randomUUID().toString();
        DemoSubscription subscription = new DemoSubscription(id, client, topic, Instant.now());
        Disposable disposable = router.getDataStream(subject).subscribe(subscription.replies::add);
        subscription.disposable = disposable;
        subscriptions.put(id, subscription);
        return "{\"status\":\"subscribed\",\"subscription\":" + subscription.json() + ",\"state\":" + state() + "}";
    }

    private String publish(URI uri) {
        Map<String, String> query = query(uri);
        String topic = required(query, "topic");
        String content = query.getOrDefault("content", "tick-" + Instant.now());
        ReplyMessage reply = ReplyMessage.create(MessageSubject.of("topic", topic), content);
        replyMessageService.getPublisher().onNext(reply);
        return "{\"status\":\"published\",\"reply\":" + replyJson(reply) + ",\"state\":" + state() + "}";
    }

    private String unsubscribe(URI uri) {
        Map<String, String> query = query(uri);
        String id = required(query, "subscriptionId");
        DemoSubscription subscription = subscriptions.remove(id);
        if (subscription == null) {
            return "{\"status\":\"not_found\",\"subscriptionId\":\"" + json(id) + "\",\"state\":" + state() + "}";
        }
        subscription.close();
        return "{\"status\":\"unsubscribed\",\"subscription\":" + subscription.json() + ",\"state\":" + state() + "}";
    }

    private String simulate(URI uri) {
        Map<String, String> query = query(uri);
        String topic = query.getOrDefault("topic", "prices");
        String[] clients = query.getOrDefault("clients", "alpha,beta,gamma").split(",");
        List<DemoSubscription> created = new ArrayList<>();
        for (String rawClient : clients) {
            String client = rawClient.trim();
            if (!client.isEmpty()) {
                Map<String, String> subscribeQuery = Map.of("topic", topic, "client", client);
                DemoSubscription subscription = createSubscription(subscribeQuery);
                created.add(subscription);
            }
        }

        ReplyMessage first = ReplyMessage.create(MessageSubject.of("topic", topic), "confirmation:" + topic);
        ReplyMessage second = ReplyMessage.create(MessageSubject.of("topic", topic), "update:" + topic);
        replyMessageService.getPublisher().onNext(first);
        replyMessageService.getPublisher().onNext(second);

        for (DemoSubscription subscription : created) {
            subscriptions.remove(subscription.id);
            subscription.close();
        }

        return "{\"status\":\"simulated\",\"topic\":\"" + json(topic) + "\",\"clients\":" + created.size()
                + ",\"requests\":" + requestsJson() + ",\"state\":" + state() + "}";
    }

    private DemoSubscription createSubscription(Map<String, String> query) {
        String topic = required(query, "topic");
        String client = query.getOrDefault("client", "browser");
        String id = UUID.randomUUID().toString();
        DemoSubscription subscription = new DemoSubscription(id, client, topic, Instant.now());
        subscription.disposable = router.getDataStream(MessageSubject.of("topic", topic)).subscribe(subscription.replies::add);
        subscriptions.put(id, subscription);
        return subscription;
    }

    private String state() {
        List<String> subscriptionJson = subscriptions.values().stream()
                .sorted(Comparator.comparing(DemoSubscription::createdAt))
                .map(DemoSubscription::json)
                .toList();
        return "{\"subscriptions\":[" + String.join(",", subscriptionJson) + "],\"upstreamRequests\":"
                + requestsJson() + "}";
    }

    private String routes() {
        return """
                {
                  "name": "ReactiveWebsockets demo",
                  "examples": [
                    "GET /api/topics",
                    "POST /api/subscribe?client=alice&topic=prices",
                    "POST /api/publish?topic=prices&content=42.10",
                    "POST /api/unsubscribe?subscriptionId=<subscription-id>",
                    "POST /api/simulate?topic=prices"
                  ]
                }
                """;
    }

    private String topics() {
        List<String> values = TOPICS.stream()
                .map(topic -> "\"" + json(topic) + "\"")
                .toList();
        return "{\"topics\":[" + String.join(",", values) + "]}";
    }

    private String requestsJson() {
        List<String> entries = upstreamRequests.stream()
                .map(ReactiveWebsocketsDemoServer::requestJson)
                .toList();
        return "[" + String.join(",", entries) + "]";
    }

    private static String requestJson(RequestMessage request) {
        return "{\"command\":\"" + request.getContent() + "\",\"subject\":" + subjectJson(request.getSubject())
                + ",\"timestamp\":\"" + request.getTimestamp() + "\"}";
    }

    private static String replyJson(ReplyMessage reply) {
        return "{\"content\":\"" + json(reply.getContent()) + "\",\"subject\":" + subjectJson(reply.getSubject())
                + ",\"timestamp\":\"" + reply.getTimestamp() + "\"}";
    }

    private static String subjectJson(MessageSubject subject) {
        List<String> fields = new ArrayList<>();
        subject.getFields().forEach((key, value) ->
                fields.add("\"" + json(key) + "\":\"" + json(value) + "\""));
        return "{" + String.join(",", fields) + "}";
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> result = new LinkedHashMap<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = java.net.URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                    ? java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            result.put(key, value);
        }
        return result;
    }

    private static String required(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static void respond(HttpExchange exchange, int status, String body) {
        respond(exchange, status, body, "application/json; charset=utf-8");
    }

    private static void respondHtml(HttpExchange exchange, int status, String body) {
        respond(exchange, status, body, "text/html; charset=utf-8");
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String json(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String ui() {
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>ReactiveWebsockets demo</title>
                    <style>
                      :root {
                        color-scheme: light;
                        font-family: Arial, Helvetica, sans-serif;
                        background: #f7f8fa;
                        color: #20242a;
                      }

                      body {
                        margin: 0;
                      }

                      main {
                        max-width: 1040px;
                        margin: 0 auto;
                        padding: 32px 20px;
                      }

                      h1 {
                        margin: 0 0 8px;
                        font-size: 32px;
                        letter-spacing: 0;
                      }

                      p {
                        margin: 0 0 24px;
                        color: #4e5965;
                        line-height: 1.5;
                      }

                      .controls {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
                        gap: 16px;
                        margin-bottom: 18px;
                      }

                      label {
                        display: grid;
                        gap: 6px;
                        font-weight: 700;
                        font-size: 14px;
                      }

                      select,
                      input,
                      button {
                        min-height: 42px;
                        border: 1px solid #c8d0d9;
                        border-radius: 6px;
                        padding: 8px 10px;
                        font: inherit;
                        background: #ffffff;
                      }

                      button {
                        border-color: #0f766e;
                        background: #0f766e;
                        color: #ffffff;
                        font-weight: 700;
                        cursor: pointer;
                      }

                      button.secondary {
                        border-color: #53616f;
                        background: #ffffff;
                        color: #24292f;
                      }

                      button:disabled {
                        cursor: wait;
                        opacity: 0.68;
                      }

                      .actions {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 10px;
                        margin-bottom: 20px;
                      }

                      .status {
                        min-height: 26px;
                        margin-bottom: 18px;
                        font-weight: 700;
                      }

                      .status[data-state="subscribed"],
                      .status[data-state="published"],
                      .status[data-state="simulated"] {
                        color: #116329;
                      }

                      .grid {
                        display: grid;
                        grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
                        gap: 18px;
                      }

                      pre {
                        min-height: 260px;
                        overflow: auto;
                        margin: 0;
                        padding: 14px;
                        border: 1px solid #d0d7de;
                        border-radius: 6px;
                        background: #ffffff;
                        font-size: 13px;
                        line-height: 1.45;
                      }
                    </style>
                  </head>
                  <body>
                    <main>
                      <h1>ReactiveWebsockets demo</h1>
                      <p>Subscribe browser clients to fake websocket topics, publish replies, and watch the router emit upstream subscribe/unsubscribe requests only when needed.</p>

                      <div class="controls" aria-label="Subscription controls">
                        <label>
                          Client
                          <input id="client" value="alice">
                        </label>
                        <label>
                          Topic
                          <select id="topic"></select>
                        </label>
                        <label>
                          Reply content
                          <input id="content" value="price=42.10">
                        </label>
                      </div>

                      <div class="actions">
                        <button id="subscribe">Subscribe</button>
                        <button id="publish" class="secondary">Publish reply</button>
                        <button id="unsubscribe" class="secondary">Unsubscribe last</button>
                        <button id="simulate" class="secondary">Simulate shared topic</button>
                      </div>

                      <div id="status" class="status" role="status" aria-live="polite">Loading topics...</div>

                      <div class="grid">
                        <section>
                          <h2>State</h2>
                          <pre id="state">{}</pre>
                        </section>
                        <section>
                          <h2>Last response</h2>
                          <pre id="output">{}</pre>
                        </section>
                      </div>
                    </main>

                    <script>
                      const topic = document.querySelector("#topic");
                      const client = document.querySelector("#client");
                      const content = document.querySelector("#content");
                      const statePanel = document.querySelector("#state");
                      const output = document.querySelector("#output");
                      const status = document.querySelector("#status");
                      const buttons = Array.from(document.querySelectorAll("button"));
                      const subscriptions = [];

                      function setBusy(isBusy) {
                        buttons.forEach((button) => {
                          button.disabled = isBusy;
                        });
                      }

                      function show(message, stateName) {
                        status.textContent = message;
                        status.dataset.state = stateName || "";
                      }

                      function render(data) {
                        output.textContent = JSON.stringify(data, null, 2);
                        if (data.state) {
                          statePanel.textContent = JSON.stringify(data.state, null, 2);
                        }
                        if (data.subscription && data.subscription.id && data.status === "subscribed") {
                          subscriptions.push(data.subscription.id);
                        }
                        if (data.status) {
                          show(data.status, data.status);
                        }
                      }

                      async function call(path, options = {}) {
                        setBusy(true);
                        try {
                          const response = await fetch(path, options);
                          const data = await response.json();
                          render(data);
                          return data;
                        } finally {
                          setBusy(false);
                        }
                      }

                      async function loadTopics() {
                        const data = await call("/api/topics");
                        topic.innerHTML = "";
                        data.topics.forEach((item) => {
                          const option = document.createElement("option");
                          option.value = item;
                          option.textContent = item;
                          topic.append(option);
                        });
                        await call("/api/state");
                        show(`Loaded ${data.topics.length} topics`, "loaded");
                      }

                      document.querySelector("#subscribe").addEventListener("click", () => {
                        const params = new URLSearchParams({ client: client.value, topic: topic.value });
                        call(`/api/subscribe?${params}`, { method: "POST" });
                      });

                      document.querySelector("#publish").addEventListener("click", () => {
                        const params = new URLSearchParams({ topic: topic.value, content: content.value });
                        call(`/api/publish?${params}`, { method: "POST" });
                      });

                      document.querySelector("#unsubscribe").addEventListener("click", () => {
                        const subscriptionId = subscriptions.pop();
                        if (!subscriptionId) {
                          show("No active subscription in this browser session", "not_found");
                          return;
                        }
                        const params = new URLSearchParams({ subscriptionId });
                        call(`/api/unsubscribe?${params}`, { method: "POST" });
                      });

                      document.querySelector("#simulate").addEventListener("click", () => {
                        const params = new URLSearchParams({ topic: topic.value, clients: "alpha,beta,gamma" });
                        call(`/api/simulate?${params}`, { method: "POST" });
                      });

                      loadTopics().catch((error) => {
                        show(error.message, "error");
                      });
                    </script>
                  </body>
                </html>
                """;
    }

    private static final class DemoSubscription {
        private final String id;
        private final String client;
        private final String topic;
        private final Instant createdAt;
        private final List<ReplyMessage> replies = new CopyOnWriteArrayList<>();
        private Disposable disposable;

        private DemoSubscription(String id, String client, String topic, Instant createdAt) {
            this.id = id;
            this.client = client;
            this.topic = topic;
            this.createdAt = createdAt;
        }

        private Instant createdAt() {
            return createdAt;
        }

        private void close() {
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
            }
        }

        private String json() {
            List<String> replyJson = replies.stream()
                    .map(ReactiveWebsocketsDemoServer::replyJson)
                    .toList();
            return "{\"id\":\"" + ReactiveWebsocketsDemoServer.json(id) + "\",\"client\":\""
                    + ReactiveWebsocketsDemoServer.json(client) + "\",\"topic\":\""
                    + ReactiveWebsocketsDemoServer.json(topic)
                    + "\",\"createdAt\":\"" + createdAt + "\",\"replies\":[" + String.join(",", replyJson) + "]}";
        }
    }
}
