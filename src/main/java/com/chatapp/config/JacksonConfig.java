package com.chatapp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global {@link ObjectMapper} configuration shared by REST, WebSocket
 * serialization, and Redis pub/sub.
 *
 * <p>
 * {@link com.fasterxml.jackson.datatype.jsr310.JavaTimeModule} is required for
 * {@code LocalDateTime} fields on
 * {@link com.chatapp.domain.message.dto.WebSocketMessage};
 * without it, serialization throws at runtime when publishing to Redis.
 * {@code WRITE_DATES_AS_TIMESTAMPS=false} emits ISO-8601 strings instead of
 * epoch arrays.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
