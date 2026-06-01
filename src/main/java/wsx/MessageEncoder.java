package wsx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;
import java.io.Writer;
import java.time.Instant;

public class MessageEncoder<M extends Message<?>> implements Encoder.TextStream<M> {

    private static final Gson gson = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .serializeSpecialFloatingPointValues()
            .create();

    @Override
    public void init(EndpointConfig config) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void encode(M message, Writer writer) throws EncodeException, IOException {
        gson.toJson(message, message.getClass(), writer);
    }
}
