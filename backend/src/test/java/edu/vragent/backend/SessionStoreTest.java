package edu.vragent.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SessionStoreTest {
    @Test
    void advancesThroughTheDemoScenario() {
        SessionStore store = new SessionStore();
        UUID id = store.create().sessionId();
        assertEquals("SCENE_SAFETY", store.get(id).stage());
        assertEquals("INITIAL_ASSESSMENT", store.advance(id).stage());
        assertEquals("COMPLETE", store.advance(id).stage());
        assertEquals("COMPLETE", store.advance(id).stage());
    }

    @Test
    void unknownSessionIsRejected() {
        SessionStore store = new SessionStore();
        assertThrows(ResponseStatusException.class, () -> store.get(UUID.randomUUID()));
    }

    @Test
    void conversationHistoryIsIsolatedLimitedAndClearable() {
        SessionStore store = new SessionStore();
        UUID first = store.create().sessionId();
        UUID second = store.create().sessionId();

        store.appendExchange(first, "My name is Alex.", "Hello Alex.");
        store.appendExchange(first, "Remember it.", "I will remember it in this session.");

        assertEquals(4, store.messages(first).size());
        assertEquals(0, store.messages(second).size());

        var context = store.conversationWith(first, "What is my name?", 3);
        assertEquals(3, context.size());
        assertEquals("assistant", context.get(1).role());
        assertEquals("What is my name?", context.get(2).content());

        store.clearMessages(first);
        assertEquals(0, store.messages(first).size());
    }
}
