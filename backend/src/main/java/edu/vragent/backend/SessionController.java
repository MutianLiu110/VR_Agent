package edu.vragent.backend;

import edu.vragent.backend.AgentClient.AgentReply;
import edu.vragent.backend.AgentClient.AgentRequest;
import edu.vragent.backend.SessionStore.SessionView;
import edu.vragent.backend.SessionStore.ChatMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/sessions", produces = "application/json;charset=UTF-8")
public class SessionController {
    private static final int MODEL_CONTEXT_MESSAGE_LIMIT = 20;
    private final SessionStore sessions;
    private final AgentClient agent;

    public SessionController(SessionStore sessions, AgentClient agent) {
        this.sessions = sessions;
        this.agent = agent;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionView create() {
        return sessions.create();
    }

    @GetMapping("/{id}")
    public SessionView get(@PathVariable UUID id) {
        return sessions.get(id);
    }

    @PostMapping("/{id}/events/advance")
    public SessionView advance(@PathVariable UUID id) {
        return sessions.advance(id);
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<AgentReply> message(@PathVariable UUID id, @Valid @RequestBody MessageRequest message) {
        String length = "detailed".equalsIgnoreCase(message.answerLength()) ? "detailed" : "short";
        SessionView session = sessions.get(id);
        var context = sessions.conversationWith(id, message.question(), MODEL_CONTEXT_MESSAGE_LIMIT);
        ResponseEntity<AgentReply> response = agent.respond(
                new AgentRequest(id, session.stage(), length, context));
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            sessions.appendExchange(id, message.question(), response.getBody().reply());
        }
        return response;
    }

    @GetMapping("/{id}/messages")
    public java.util.List<ChatMessage> messages(@PathVariable UUID id) {
        return sessions.messages(id);
    }

    @DeleteMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearMessages(@PathVariable UUID id) {
        sessions.clearMessages(id);
    }

    public record MessageRequest(@NotBlank String question, String answerLength) {}
}
