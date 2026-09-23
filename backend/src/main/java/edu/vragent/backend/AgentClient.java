package edu.vragent.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import edu.vragent.backend.SessionStore.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AgentClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
    private final ObjectMapper json;
    private final URI agentUri;

    public AgentClient(ObjectMapper json, @Value("${agent.url:http://127.0.0.1:8081}") String agentUrl) {
        this.json = json;
        this.agentUri = URI.create(agentUrl + "/internal/respond");
    }

    public ResponseEntity<AgentReply> respond(AgentRequest request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(agentUri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 504) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent service returned an error");
            }
            AgentReply reply = json.readValue(response.body(), AgentReply.class);
            return ResponseEntity.status(response.statusCode()).body(reply);
        } catch (HttpTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new AgentReply("The Java backend timed out while waiting for the Agent service.",
                            null, "timeout"));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent service is unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Agent request was interrupted", e);
        }
    }

    public record AgentRequest(UUID sessionId, String stage, String answerLength, List<ChatMessage> messages) {}
    public record AgentReply(String reply, String suggestedAdjustment, String mode) {}
}
