package com.rappi.purchasing.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Works with any OpenAI-compatible /chat/completions endpoint that supports tool calling. */
public class OpenAiLlmClient implements LlmClient {
    private final RestClient http = RestClient.create();
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OpenAiLlmClient(ObjectMapper mapper, String baseUrl, String apiKey, String model) {
        this.mapper = mapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public ObjectNode chat(List<ObjectNode> messages, List<ToolSpec> tools) {
        ObjectNode body = mapper.createObjectNode().put("model", model);
        body.set("messages", mapper.valueToTree(messages));
        ArrayNode toolArray = body.putArray("tools");
        for (ToolSpec t : tools) {
            ObjectNode fn = toolArray.addObject().put("type", "function").putObject("function");
            fn.put("name", t.name()).put("description", t.description()).set("parameters", t.parameters());
        }
        String response = http.post().uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString())
                .retrieve().body(String.class);
        try {
            JsonNode msg = mapper.readTree(response).path("choices").path(0).path("message");
            // keep only the fields we need to send back on the next turn
            ObjectNode clean = mapper.createObjectNode().put("role", "assistant");
            if (msg.hasNonNull("content")) clean.put("content", msg.get("content").asText());
            if (msg.hasNonNull("tool_calls")) clean.set("tool_calls", msg.get("tool_calls"));
            return clean;
        } catch (Exception e) {
            throw new IllegalStateException("Bad LLM response: " + response, e);
        }
    }
}
