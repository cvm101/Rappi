package com.rappi.purchasing.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LlmConfig {
    @Bean
    public LlmClient llmClient(ObjectMapper mapper,
                               @Value("${llm.provider}") String provider,
                               @Value("${llm.base-url}") String baseUrl,
                               @Value("${llm.api-key}") String apiKey,
                               @Value("${llm.model}") String model,
                               @Value("${llm.mock-naive-first}") boolean naiveFirst) {
        if ("openai".equalsIgnoreCase(provider)) {
            return new OpenAiLlmClient(mapper, baseUrl, apiKey, model);
        }
        return new MockLlmClient(mapper, naiveFirst);
    }
}
