package wsx;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.Instant;

/**
 * Gson adapter that serializes {@link Instant} values as ISO-8601 strings.
 */
final class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {

    /**
     * Serializes an instant into a JSON string.
     *
     * @param src instant to serialize
     * @param srcType source type provided by Gson
     * @param context serialization context
     * @return JSON string primitive
     */
    @Override
    public JsonElement serialize(Instant src, Type srcType, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }

    /**
     * Deserializes an ISO-8601 JSON string into an instant.
     *
     * @param json JSON element containing the instant string
     * @param type target type provided by Gson
     * @param context deserialization context
     * @return parsed instant
     * @throws JsonParseException if the value cannot be parsed as an instant
     */
    @Override
    public Instant deserialize(JsonElement json, Type type, JsonDeserializationContext context)
            throws JsonParseException {
        return Instant.parse(json.getAsString());
    }
}
