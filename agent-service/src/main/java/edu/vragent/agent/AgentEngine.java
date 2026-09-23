package edu.vragent.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.vragent.agent.AgentController.AgentReply;
import edu.vragent.agent.AgentController.AgentRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentEngine {
    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final Map<String, String> APPROVED_STAGE_NOTES = Map.of(
            "SCENE_SAFETY", "This is a simulated scene-safety stage. Ask the learner to follow the instructor-approved scene checklist before advancing.",
            "INITIAL_ASSESSMENT", "This is a simulated initial-assessment stage. Ask the learner to follow the instructor-approved assessment checklist. Do not invent clinical steps.",
            "COMPLETE", "The short training scenario is complete. Invite the learner to review the instructor-approved feedback."
    );

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;

    public AgentEngine(ObjectMapper json,
                       @Value("${OPENAI_API_KEY:}") String apiKey,
                       @Value("${OPENAI_MODEL:}") String model) {
        this.json = json;
        this.apiKey = apiKey.strip();
        this.model = model.strip();
    }

    public AgentReply respond(AgentRequest request) {
        String note = APPROVED_STAGE_NOTES.get(request.stage());
        if (note == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown scenario stage");
        }
        String suggestion = suggestsQuiet(request.question()) ? "REDUCE_BACKGROUND_SOUND" : null;
        if (apiKey.isEmpty()) {
            return new AgentReply("Agent timed out in demo mode: no API key is configured.", null, "timeout");
        }
        if (model.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "OPENAI_MODEL must be set when OPENAI_API_KEY is set");
        }
        try {
            return new AgentReply(callModel(request, note), suggestion, "openai");
        } catch (HttpTimeoutException e) {
            return new AgentReply("Agent request timed out. Please try again.", null, "timeout");
        }
    }

    private String callModel(AgentRequest request, String stageNote) throws HttpTimeoutException {
        try {
            String instructions = "You are a supportive virtual character in an educational VR prototype. "
                    + "Speak clearly and without stereotypes. Answer only about the provided simulated stage. "
                    + "The stage note is the only approved scenario guidance. If it is insufficient, say so and "
                    + "refer the learner to the instructor-approved checklist. Do not give real-world medical advice. "
                    + "Never claim to have changed the VR environment. "
                    + ("detailed".equalsIgnoreCase(request.answerLength())
                    ? "Give a concise explanation with a little context."
                    : "Keep the answer to one or two short sentences.");
            String input = "Stage: " + request.stage() + "\nApproved stage note: " + stageNote
                    + "\nLearner question: " + request.question();
            String body = json.writeValueAsString(Map.of(
                    "model", model,
                    "instructions", instructions,
                    "input", input,
                    "store", false
            ));
            HttpRequest httpRequest = HttpRequest.newBuilder(RESPONSES_URI)
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "Model provider returned HTTP " + response.statusCode());
            }
            JsonNode root = json.readTree(response.body());
            StringBuilder answer = new StringBuilder();
            for (JsonNode item : root.path("output")) {
                if (!"message".equals(item.path("type").asText())) {
                    continue;
                }
                for (JsonNode content : item.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) {
                        answer.append(content.path("text").asText());
                    }
                }
            }
            if (answer.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Model returned no text");
            }
            return answer.toString();
        } catch (HttpTimeoutException e) {
            throw e;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Model provider is unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Model request was interrupted", e);
        }
    }

    private static boolean suggestsQuiet(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("noise") || normalized.contains("sound")
                || normalized.contains("吵") || normalized.contains("声音");
    }

}
