package com.rappi.purchasing.agent;

import com.fasterxml.jackson.databind.JsonNode;

/** Tool definition shown to the LLM (JSON-schema parameters). */
public record ToolSpec(String name, String description, JsonNode parameters) {}
