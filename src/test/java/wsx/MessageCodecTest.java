package wsx;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class MessageCodecTest {

    @Test
    public void requestEncoderAndDecoderRoundTripMessage() throws Exception {
        MessageSubject subject = MessageSubject.of("topic", "prices");
        Instant timestamp = Instant.parse("2026-06-02T01:02:03Z");
        RequestMessage message = RequestMessage.create(subject, RequestMessageType.Subscribe, timestamp);
        RequestMessageEncoder encoder = new RequestMessageEncoder();
        RequestMessageDecoder decoder = new RequestMessageDecoder();
        StringWriter writer = new StringWriter();

        encoder.init(null);
        encoder.encode(message, writer);
        encoder.destroy();
        decoder.init(null);
        RequestMessage decoded = decoder.decode(new StringReader(writer.toString()));
        decoder.destroy();

        assertEquals(RequestMessageType.Subscribe, decoded.getContent());
        assertEquals(subject, decoded.getSubject());
        assertEquals(timestamp, decoded.getTimestamp());
    }

    @Test
    public void replyEncoderAndDecoderRoundTripMessage() throws Exception {
        MessageSubject subject = MessageSubject.of("topic", "prices");
        Instant timestamp = Instant.parse("2026-06-02T01:02:03Z");
        ReplyMessage message = ReplyMessage.create(subject, "42", timestamp);
        ReplyMessageEncoder encoder = new ReplyMessageEncoder();
        ReplyMessageDecoder decoder = new ReplyMessageDecoder();
        StringWriter writer = new StringWriter();

        encoder.init(null);
        encoder.encode(message, writer);
        encoder.destroy();
        decoder.init(null);
        ReplyMessage decoded = decoder.decode(new StringReader(writer.toString()));
        decoder.destroy();

        assertEquals("42", decoded.getContent());
        assertEquals(subject, decoded.getSubject());
        assertEquals(timestamp, decoded.getTimestamp());
    }

    @Test
    public void instantTypeAdapterSerializesIsoInstant() {
        InstantTypeAdapter adapter = new InstantTypeAdapter();
        Instant timestamp = Instant.parse("2026-06-02T01:02:03Z");

        assertEquals("\"2026-06-02T01:02:03Z\"",
                adapter.serialize(timestamp, Instant.class, null).toString());
        assertEquals(timestamp, adapter.deserialize(adapter.serialize(timestamp, Instant.class, null),
                Instant.class, null));
    }
}
