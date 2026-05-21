package com.chatapp.config;

import com.chatapp.infrastructure.redis.RedisDmSubscriber;
import com.chatapp.infrastructure.redis.RedisMessageSubscriber;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis infrastructure configuration: custom {@code RedisTemplate} for pub/sub
 * and the
 * message listener container that routes the {@code chat:messages} channel to
 * {@link com.chatapp.infrastructure.redis.RedisMessageSubscriber}.
 *
 * <p>
 * Spring Boot auto-configures a {@code StringRedisTemplate} bean — do NOT
 * redeclare it.
 * Only the named {@code pubSubRedisTemplate} bean is defined here; it uses
 * {@link com.fasterxml.jackson.databind.ObjectMapper}-backed JSON serialization
 * to allow
 * complex objects ({@link com.chatapp.domain.message.dto.WebSocketMessage}) to
 * be published
 * and deserialized by subscribers across instances.
 */
@Configuration
public class RedisConfig {

    public static final String CHAT_TOPIC = "chat:messages";
    public static final String DM_TOPIC   = "chat:dm";

    /**
     * Custom {@link RedisTemplate} with JSON value serialization for pub/sub
     * operations.
     * Injected via {@code @Qualifier("pubSubRedisTemplate")} in
     * {@link com.chatapp.domain.message.service.MessageService} and
     * {@link com.chatapp.websocket.ChatMessageController}.
     */
    @Bean("pubSubRedisTemplate")
    public RedisTemplate<String, Object> pubSubRedisTemplate(RedisConnectionFactory connectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        return template;
    }

    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic(CHAT_TOPIC);
    }

    @Bean
    public ChannelTopic dmTopic() {
        return new ChannelTopic(DM_TOPIC);
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter(RedisMessageSubscriber subscriber,
            ObjectMapper objectMapper) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public MessageListenerAdapter dmListenerAdapter(RedisDmSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter messageListenerAdapter,
            MessageListenerAdapter dmListenerAdapter,
            ChannelTopic chatTopic,
            ChannelTopic dmTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(messageListenerAdapter, chatTopic);
        container.addMessageListener(dmListenerAdapter, dmTopic);
        return container;
    }
}
