package edu.vragent.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionStore {
    private static final Map<String, String> NEXT_STAGE = Map.of(
            "SCENE_SAFETY", "INITIAL_ASSESSMENT",
            "INITIAL_ASSESSMENT", "COMPLETE"
    );

    private final ConcurrentHashMap<UUID, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionView create() {
        UUID id = UUID.randomUUID();
        sessions.put(id, new SessionState("SCENE_SAFETY"));
        return new SessionView(id, "SCENE_SAFETY");
    }

    public SessionView get(UUID id) {
        SessionState state = requireSession(id);
        synchronized (state) {
            return new SessionView(id, state.stage);
        }
    }

    public SessionView advance(UUID id) {
        SessionState state = requireSession(id);
        synchronized (state) {
            state.stage = NEXT_STAGE.getOrDefault(state.stage, state.stage);
            return new SessionView(id, state.stage);
        }
    }

    /** Builds model context without committing the pending user message. */
    public List<ChatMessage> conversationWith(UUID id, String userMessage, int maximumMessages) {
        if (maximumMessages < 1) {
            throw new IllegalArgumentException("maximumMessages must be at least one");
        }
        SessionState state = requireSession(id);
        synchronized (state) {
            int historyLimit = maximumMessages - 1;
            // Stored history is made of user/assistant pairs; never cut a pair in half.
            if (historyLimit % 2 != 0) {
                historyLimit--;
            }
            int from = Math.max(0, state.messages.size() - historyLimit);
            List<ChatMessage> context = new ArrayList<>(state.messages.subList(from, state.messages.size()));
            context.add(new ChatMessage("user", userMessage));
            return List.copyOf(context);
        }
    }

    public void appendExchange(UUID id, String userMessage, String assistantMessage) {
        SessionState state = requireSession(id);
        synchronized (state) {
            state.messages.add(new ChatMessage("user", userMessage));
            state.messages.add(new ChatMessage("assistant", assistantMessage));
        }
    }

    public List<ChatMessage> messages(UUID id) {
        SessionState state = requireSession(id);
        synchronized (state) {
            return List.copyOf(state.messages);
        }
    }

    public void clearMessages(UUID id) {
        SessionState state = requireSession(id);
        synchronized (state) {
            state.messages.clear();
        }
    }

    private SessionState requireSession(UUID id) {
        SessionState state = sessions.get(id);
        if (state == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return state;
    }

    private static final class SessionState {
        private String stage;
        private final List<ChatMessage> messages = new ArrayList<>();

        private SessionState(String stage) {
            this.stage = stage;
        }
    }

    public record SessionView(UUID sessionId, String stage) {}

    public record ChatMessage(String role, String content) {}
}
