package edu.vragent.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.vragent.backend.AgentClient.AgentReply;
import edu.vragent.backend.AgentClient.AgentRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SessionControllerTest {
    @Test
    void successfulRepliesAreRememberedAndSentOnTheNextTurn() {
        SessionStore store = new SessionStore();
        AgentClient agent = mock(AgentClient.class);
        when(agent.respond(any()))
                .thenReturn(ResponseEntity.ok(new AgentReply("Hello Alex.", null, "openai")))
                .thenReturn(ResponseEntity.ok(new AgentReply("Your name is Alex.", null, "openai")));
        SessionController controller = new SessionController(store, agent);
        UUID id = controller.create().sessionId();

        controller.message(id, new SessionController.MessageRequest("My name is Alex.", "short"));
        controller.message(id, new SessionController.MessageRequest("What is my name?", "short"));

        ArgumentCaptor<AgentRequest> requests = ArgumentCaptor.forClass(AgentRequest.class);
        org.mockito.Mockito.verify(agent, org.mockito.Mockito.times(2)).respond(requests.capture());
        AgentRequest second = requests.getAllValues().get(1);
        assertEquals(id, second.sessionId());
        assertEquals(3, second.messages().size());
        assertEquals("My name is Alex.", second.messages().get(0).content());
        assertEquals("Hello Alex.", second.messages().get(1).content());
        assertEquals("What is my name?", second.messages().get(2).content());
        assertEquals(4, controller.messages(id).size());
    }

    @Test
    void timeoutDoesNotPolluteConversationHistory() {
        SessionStore store = new SessionStore();
        AgentClient agent = mock(AgentClient.class);
        when(agent.respond(any())).thenReturn(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new AgentReply("Agent timed out.", null, "timeout")));
        SessionController controller = new SessionController(store, agent);
        UUID id = controller.create().sessionId();

        controller.message(id, new SessionController.MessageRequest("Are you there?", "short"));

        assertEquals(0, controller.messages(id).size());
    }
}
