package wsx;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.joda.time.DateTime;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import java.io.IOException;
import java.io.Reader;

public abstract class MessageDecoder<M extends Message<?>> implements Decoder.TextStream<M> {

    private static Gson gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .enableComplexMapKeySerialization()
            .registerTypeAdapter(DateTime.class, new DateTimeTypeConverter<>())
            .serializeSpecialFloatingPointValues()
            .create();

    private final TypeToken<M> typeToken = new TypeToken<M>(getClass()) {
        private static final long serialVersionUID = 7826774195137075501L;
    };

    @Override
    public void init(EndpointConfig config) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public M decode(Reader reader) throws DecodeException, IOException {
        return gson.fromJson(reader, typeToken.getType());
    }
}
