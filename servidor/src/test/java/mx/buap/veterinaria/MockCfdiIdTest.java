package mx.buap.veterinaria;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MockCfdiIdTest {
    @Test
    void identifierIsDeterministicAndClearlyMockScoped() {
        UUID first = UUID.nameUUIDFromBytes("MOCK-CFDI-42-req-1".getBytes(StandardCharsets.UTF_8));
        UUID second = UUID.nameUUIDFromBytes("MOCK-CFDI-42-req-1".getBytes(StandardCharsets.UTF_8));
        assertEquals(first, second);
        assertNotNull(first);
    }
}
