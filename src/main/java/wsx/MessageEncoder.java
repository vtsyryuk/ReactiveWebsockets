package wsx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;

/**
 * Gson-backed websocket text encoder for {@link Message} subclasses.
 *
 * @param <M> message type to encode
 */
public class MessageEncoder<M extends Message<?>> implements Encoder.TextStream<M> {

    private static final Gson gson = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .serializeSpecialFloatingPointValues()
            .create();

    @Override
    public void init(EndpointConfig config) {
        // No encoder state is initialized by this stateless Gson adapter.
    }

    @Override
    public void destroy() {
        // No encoder resources are allocated by this stateless Gson adapter.
    }

    /**
     * Encodes a message as JSON using the concrete message class.
     *
     * @param message message to encode
     * @param writer destination writer supplied by the websocket runtime
     * @throws EncodeException if the websocket runtime cannot encode the message
     * @throws IOException if writing the JSON fails
     */
    @Override
    public void encode(M message, Writer writer) throws EncodeException, IOException {
        gson.toJson(message, message.getClass(), writer);
    }
}
