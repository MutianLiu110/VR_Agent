package edu.vragent.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentController {
    private final AgentEngine engine;

    public AgentController(AgentEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @PostMapping("/internal/respond")
    public ResponseEntity<AgentReply> respond(@Valid @RequestBody AgentRequest request) {
        AgentReply reply = engine.respond(request);
        HttpStatus status = "timeout".equals(reply.mode()) ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.OK;
        return ResponseEntity.status(status).body(reply);
    }

    public record AgentRequest(@NotBlank String stage, @NotBlank String question, String answerLength) {}
    public record AgentReply(String reply, String suggestedAdjustment, String mode) {}
}
