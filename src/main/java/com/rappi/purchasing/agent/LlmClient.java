package com.rappi.purchasing.agent;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Minimal chat-completions abstraction (OpenAI message format).
 * Returns the assistant message: either {"tool_calls": [...]} or {"content": "..."}.
 */
public interface LlmClient {
    ObjectNode chat(List<ObjectNode> messages, List<ToolSpec> tools);
}
