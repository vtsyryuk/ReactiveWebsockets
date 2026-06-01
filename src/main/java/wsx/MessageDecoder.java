package wsx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;

public abstract class MessageDecoder<M extends Message<?>> implements Decoder.TextStream<M> {

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
    public M decode(Reader reader) throws DecodeException, IOException {
        return gson.fromJson(reader, messageClass());
    }

    protected abstract Class<M> messageClass();
}
