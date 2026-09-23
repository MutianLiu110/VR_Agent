package edu.vragent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.vragent.agent.AgentController.AgentReply;
import edu.vragent.agent.AgentController.AgentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AgentEngineTest {
    private final AgentEngine engine = new AgentEngine(new ObjectMapper(), "", "");

    @Test
    void noKeyProducesTimeoutWithoutInventingAnAnswerOrSuggestion() {
        AgentReply reply = engine.respond(new AgentRequest("SCENE_SAFETY", "声音太吵了", "short"));
        assertEquals("timeout", reply.mode());
        assertEquals(null, reply.suggestedAdjustment());
        assertEquals("Agent timed out in demo mode: no API key is configured.", reply.reply());
        assertEquals("timeout", engine.respond(new AgentRequest("COMPLETE", "What next?", "detailed")).mode());
    }

    @Test
    void unknownStageIsRejected() {
        assertThrows(ResponseStatusException.class,
                () -> engine.respond(new AgentRequest("UNAPPROVED", "What next?", "short")));
    }
}
