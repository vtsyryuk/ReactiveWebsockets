package wsx.demo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
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
import java.lang.reflect.Type;
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

/**
 * Small HTTP demo that exercises the library without requiring a real websocket server.
 *
 * <p>The demo keeps all state in memory, exposes JSON endpoints for scripted checks, and serves a browser UI from
 * classpath resources. It is intentionally excluded from published library artifacts and packaged separately for the
 * runnable application distribution.</p>
 */
public final class ReactiveWebsocketsDemoServer {

    private static final int DEFAULT_PORT = 8080;
    private static final String TOPIC_FIELD = "topic";
    private static final String CLIENT_FIELD = "client";
    private static final String CLIENTS_FIELD = "clients";
    private static final String SUBSCRIPTION_ID_FIELD = "subscriptionId";
    private static final String CONTENT_FIELD = "content";
    private static final List<String> TOPICS = List.of("prices", "orders", "alerts", "inventory");
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonSerializer<Instant>) ReactiveWebsocketsDemoServer::serializeInstant)
            .create();

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
            Route route = Route.match(method, path);
            if (route == null) {
                respond(exchange, 404, new ErrorResponse("not_found"));
                return;
            }
            switch (route) {
                case ROOT -> respondHtml(exchange, 200, ui());
                case UI_CSS -> respondCss(exchange, 200, resource("/ui.css"));
                case API -> respond(exchange, 200, routes());
                case HEALTH -> respond(exchange, 200, new StatusResponse("ok"));
                case TOPICS -> respond(exchange, 200, topics());
                case STATE -> respond(exchange, 200, state());
                case SUBSCRIBE -> respond(exchange, 200, subscribe(exchange.getRequestURI()));
                case PUBLISH -> respond(exchange, 200, publish(exchange.getRequestURI()));
                case UNSUBSCRIBE -> respond(exchange, 200, unsubscribe(exchange.getRequestURI()));
                case SIMULATE -> respond(exchange, 200, simulate(exchange.getRequestURI()));
            }
        } catch (IllegalArgumentException ex) {
            respond(exchange, 400, new ErrorResponse(ex.getMessage()));
        } catch (Exception ex) {
            respond(exchange, 500, new ErrorResponse(ex.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private SubscribeResponse subscribe(URI uri) {
        SubscribeRequest request = SubscribeRequest.from(query(uri));
        DemoSubscription subscription = createSubscription(request);
        return new SubscribeResponse("subscribed", subscription.snapshot(), state());
    }

    private PublishResponse publish(URI uri) {
        PublishRequest request = PublishRequest.from(query(uri));
        ReplyMessage reply = ReplyMessage.create(topicSubject(request.topic()), request.content());
        replyMessageService.getPublisher().onNext(reply);
        return new PublishResponse("published", toReplyView(reply), state());
    }

    private UnsubscribeResponse unsubscribe(URI uri) {
        UnsubscribeRequest request = UnsubscribeRequest.from(query(uri));
        String id = request.subscriptionId();
        DemoSubscription subscription = subscriptions.remove(id);
        if (subscription == null) {
            return new UnsubscribeResponse("not_found", id, null, state());
        }
        subscription.close();
        return new UnsubscribeResponse("unsubscribed", id, subscription.snapshot(), state());
    }

    private SimulatedResponse simulate(URI uri) {
        SimulateRequest request = SimulateRequest.from(query(uri));
        List<DemoSubscription> created = new ArrayList<>();
        for (String client : request.clients()) {
            if (!client.isEmpty()) {
                DemoSubscription subscription = createSubscription(new SubscribeRequest(request.topic(), client));
                created.add(subscription);
            }
        }

        ReplyMessage first = ReplyMessage.create(topicSubject(request.topic()), "confirmation:" + request.topic());
        ReplyMessage second = ReplyMessage.create(topicSubject(request.topic()), "update:" + request.topic());
        replyMessageService.getPublisher().onNext(first);
        replyMessageService.getPublisher().onNext(second);

        for (DemoSubscription subscription : created) {
            subscriptions.remove(subscription.id);
            subscription.close();
        }

        return new SimulatedResponse("simulated", request.topic(), created.size(), requests(), state());
    }

    private DemoSubscription createSubscription(SubscribeRequest request) {
        String id = UUID.randomUUID().toString();
        DemoSubscription subscription = new DemoSubscription(id, request.client(), request.topic(), Instant.now());
        subscription.disposable = router.getDataStream(topicSubject(request.topic())).subscribe(subscription.replies::add);
        subscriptions.put(id, subscription);
        return subscription;
    }

    private StateResponse state() {
        return new StateResponse(subscriptions(), requests());
    }

    private RoutesResponse routes() {
        return new RoutesResponse("ReactiveWebsockets demo", List.of(
                "GET /api/topics",
                "POST /api/subscribe?client=alice&topic=prices",
                "POST /api/publish?topic=prices&content=42.10",
                "POST /api/unsubscribe?subscriptionId=<subscription-id>",
                "POST /api/simulate?topic=prices"
        ));
    }

    private TopicsResponse topics() {
        return new TopicsResponse(TOPICS);
    }

    private List<RequestView> requests() {
        return upstreamRequests.stream()
                .map(ReactiveWebsocketsDemoServer::toRequestView)
                .toList();
    }

    private List<SubscriptionView> subscriptions() {
        return subscriptions.values().stream()
                .sorted(Comparator.comparing(DemoSubscription::createdAt))
                .map(DemoSubscription::snapshot)
                .toList();
    }

    private static RequestView toRequestView(RequestMessage request) {
        return new RequestView(request.getContent().getValue(), request.getSubject().getFields(), request.getTimestamp());
    }

    private static ReplyView toReplyView(ReplyMessage reply) {
        return new ReplyView(reply.getContent(), reply.getSubject().getFields(), reply.getTimestamp());
    }

    private static MessageSubject topicSubject(String topic) {
        return MessageSubject.of(TOPIC_FIELD, topic);
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

    private static void respond(HttpExchange exchange, int status, Object body) {
        respond(exchange, status, GSON.toJson(body));
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

    private static String ui() {
        return resource("/ui.html");
    }

    private static String resource(String path) {
        try (var inputStream = ReactiveWebsocketsDemoServer.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalStateException(path.substring(1) + " not found in resources");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void respondCss(HttpExchange exchange, int status, String body) {
        respond(exchange, status, body, "text/css; charset=utf-8");
    }

    private static JsonElement serializeInstant(Instant src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
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

        private SubscriptionView snapshot() {
            return new SubscriptionView(id, client, topic, createdAt, replies.stream()
                    .map(ReactiveWebsocketsDemoServer::toReplyView)
                    .toList());
        }
    }

    private record SubscribeRequest(String topic, String client) {
        private static SubscribeRequest from(Map<String, String> query) {
            return new SubscribeRequest(required(query, TOPIC_FIELD), query.getOrDefault(CLIENT_FIELD, "browser"));
        }
    }

    private record PublishRequest(String topic, String content) {
        private static PublishRequest from(Map<String, String> query) {
            return new PublishRequest(required(query, TOPIC_FIELD), query.getOrDefault(CONTENT_FIELD,
                    "tick-" + Instant.now()));
        }
    }

    private record UnsubscribeRequest(String subscriptionId) {
        private static UnsubscribeRequest from(Map<String, String> query) {
            return new UnsubscribeRequest(required(query, SUBSCRIPTION_ID_FIELD));
        }
    }

    private record SimulateRequest(String topic, List<String> clients) {
        private static SimulateRequest from(Map<String, String> query) {
            String topic = query.getOrDefault(TOPIC_FIELD, "prices");
            List<String> clients = java.util.Arrays.stream(query.getOrDefault(CLIENTS_FIELD, "alpha,beta,gamma").split(","))
                    .map(String::trim)
                    .filter(client -> !client.isEmpty())
                    .toList();
            return new SimulateRequest(topic, clients);
        }
    }

    private record RoutesResponse(String name, List<String> examples) {
    }

    private record StatusResponse(String status) {
    }

    private record ErrorResponse(String error) {
    }

    private record TopicsResponse(List<String> topics) {
    }

    private record RequestView(String command, Map<String, String> subject, Instant timestamp) {
    }

    private record ReplyView(String content, Map<String, String> subject, Instant timestamp) {
    }

    private record SubscriptionView(String id, String client, String topic, Instant createdAt,
                                    List<ReplyView> replies) {
    }

    private record StateResponse(List<SubscriptionView> subscriptions, List<RequestView> upstreamRequests) {
    }

    private record SubscribeResponse(String status, SubscriptionView subscription, StateResponse state) {
    }

    private record PublishResponse(String status, ReplyView reply, StateResponse state) {
    }

    private record UnsubscribeResponse(String status, String subscriptionId, SubscriptionView subscription,
                                       StateResponse state) {
    }

    private record SimulatedResponse(String status, String topic, int clients, List<RequestView> requests,
                                     StateResponse state) {
    }

    private enum Route {
        ROOT("GET", "/"),
        UI_CSS("GET", "/ui.css"),
        API("GET", "/api"),
        HEALTH("GET", "/health"),
        TOPICS("GET", "/api/topics"),
        STATE("GET", "/api/state"),
        SUBSCRIBE("POST", "/api/subscribe"),
        PUBLISH("POST", "/api/publish"),
        UNSUBSCRIBE("POST", "/api/unsubscribe"),
        SIMULATE("POST", "/api/simulate");

        private final String method;
        private final String path;

        Route(String method, String path) {
            this.method = method;
            this.path = path;
        }

        private static Route match(String method, String path) {
            for (Route route : values()) {
                if (route.method.equals(method) && route.path.equals(path)) {
                    return route;
                }
            }
            return null;
        }
    }
}
