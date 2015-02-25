package wsx;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.joda.time.DateTime;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;
import java.io.Writer;

public class MessageEncoder<M extends Message<?>> implements Encoder.TextStream<M> {

    private static Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter<>())
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

