package wsx;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ValueObjectTest {

    @Test
    public void diagnosticLevelPredicatesMatchTheirLevels() {
        assertTrue(DiagnosticLevel.FATAL.isFatal());
        assertTrue(DiagnosticLevel.ERROR.isError());
        assertTrue(DiagnosticLevel.WARN.isWarn());
        assertTrue(DiagnosticLevel.INFO.isInfo());
        assertTrue(DiagnosticLevel.DEBUG.isDebug());
        assertTrue(DiagnosticLevel.TRACE.isTrace());
        assertFalse(DiagnosticLevel.INFO.isError());
        assertEquals("INFO", DiagnosticLevel.INFO.toString());
        assertEquals(6, DiagnosticLevel.INFO.getValue());
    }

    @Test
    public void diagnosticMessageExposesLevelAndMessage() {
        DiagnosticMessage message = new DiagnosticMessage(DiagnosticLevel.ERROR, "boom");

        assertEquals(DiagnosticLevel.ERROR, message.getLevel());
        assertEquals(DiagnosticLevel.ERROR, message.getLeft());
        assertEquals("boom", message.getMessage());
        assertEquals("boom", message.getRight());
        assertEquals("DiagnosticMessage [level=ERROR, message=boom]", message.toString());
    }

    @Test
    public void diagnosticMessageServiceExposesPublisherAndStream() {
        DiagnosticMessageService service = new DiagnosticMessageService();
        java.util.List<DiagnosticMessage> messages = new java.util.ArrayList<>();

        service.getStream().subscribe(messages::add);
        service.getPublisher().onNext(new DiagnosticMessage(DiagnosticLevel.INFO, "hello"));

        assertEquals(1, messages.size());
        assertEquals("hello", messages.get(0).getMessage());
    }

    @Test
    public void messageSubjectIsImmutableAndComparable() {
        Map<String, String> fields = new HashMap<>();
        fields.put("topic", "prices");
        MessageSubject subject = new MessageSubject(fields);
        fields.put("topic", "changed");

        assertEquals(MessageSubject.of("topic", "prices"), subject);
        assertEquals(MessageSubject.of("topic", "prices").hashCode(), subject.hashCode());
        assertNotEquals(MessageSubject.of("topic", "changed"), subject);
        assertThrows(UnsupportedOperationException.class, () -> subject.getFields().put("x", "y"));
        assertEquals("{topic=prices}", subject.toString());
    }

    @Test
    public void messageToStringContainsTimestampContentAndSubject() {
        MessageSubject subject = MessageSubject.of("topic", "prices");
        Message<String> message = new Message<>();
        message.setSubject(subject);
        message.setContent("payload");
        message.setTimestamp(Instant.parse("2026-06-02T01:02:03Z"));

        assertEquals(subject, message.getSubject());
        assertEquals("payload", message.getContent());
        assertTrue(message.toString().contains("payload"));
        assertTrue(message.toString().contains("topic=prices"));
    }
}
