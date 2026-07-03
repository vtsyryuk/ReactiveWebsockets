package wsx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;
import java.io.Reader;
import java.time.Instant;

/**
 * Gson-backed websocket text decoder for {@link Message} subclasses.
 *
 * @param <M> message type to decode
 */
public abstract class MessageDecoder<M extends Message<?>> implements Decoder.TextStream<M> {

    private static final Gson gson = new GsonBuilder()
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .serializeSpecialFloatingPointValues()
            .create();

    @Override
    public void init(EndpointConfig config) {
        // No decoder state is initialized by this stateless Gson adapter.
    }

    @Override
    public void destroy() {
        // No decoder resources are allocated by this stateless Gson adapter.
    }

    /**
     * Decodes JSON from the websocket stream into the configured message class.
     *
     * @param reader source reader supplied by the websocket runtime
     * @return decoded message
     * @throws DecodeException if the websocket runtime cannot decode the message
     * @throws IOException if reading the JSON fails
     */
    @Override
    public M decode(Reader reader) throws DecodeException, IOException {
        return gson.fromJson(reader, messageClass());
    }

    /**
     * Returns the concrete message class used by Gson during decoding.
     *
     * @return message class
     */
    protected abstract Class<M> messageClass();
}
