package com.chatapp.config;

import com.chatapp.infrastructure.redis.PresenceService;
import com.chatapp.security.CustomUserDetailsService;
import com.chatapp.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration with JWT authentication and presence heartbeat.
 *
 * <p>Because HTTP filters ({@link com.chatapp.security.JwtAuthFilter}) do not cover the WebSocket
 * upgrade handshake, JWT authentication is performed in a {@link ChannelInterceptor} on the inbound
 * STOMP channel. The JWT must be sent in the STOMP {@code CONNECT} frame's {@code Authorization}
 * header; it is not re-validated on subsequent frames (the established {@code Principal} is
 * reused).
 *
 * <p>Each non-CONNECT inbound frame refreshes the sender's presence TTL in Redis, acting as an
 * implicit heartbeat to handle ungraceful disconnects.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final JwtService jwtService;
  private final CustomUserDetailsService userDetailsService;
  private final PresenceService presenceService;

  /**
   * Registers the STOMP endpoint at {@code /ws/chat} with SockJS fallback. SockJS CORS is
   * configured separately here (not via {@link com.chatapp.config.SecurityConfig}) because
   * WebSocket-level CORS is managed by the WebSocket container, not the servlet filter chain.
   */
  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws/chat").setAllowedOriginPatterns("*").withSockJS();
  }

  /**
   * Configures the message broker.
   *
   * <ul>
   *   <li>{@code /topic} — broadcast destinations (room messages, presence events)
   *   <li>{@code /queue} — user-specific destinations (direct messages)
   * </ul>
   *
   * {@code setUserDestinationPrefix("/user")} enables {@code convertAndSendToUser}, which routes to
   * {@code /user/{username}/queue/...} on the connected instance.
   */
  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic", "/queue");
    registry.setApplicationDestinationPrefixes("/app");
    registry.setUserDestinationPrefix("/user");
  }

  /**
   * Registers the STOMP inbound channel interceptor that:
   *
   * <ol>
   *   <li>On {@code CONNECT}: validates the JWT from the {@code Authorization} header and sets the
   *       STOMP {@code Principal}. Throws {@link MessageDeliveryException} (sends STOMP ERROR frame
   *       and closes connection) if the token is missing or invalid.
   *   <li>On all other frames: refreshes the sender's presence TTL in Redis.
   * </ol>
   */
  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(
        new ChannelInterceptor() {
          @Override
          public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            if (accessor == null) return message;

            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
              String authHeader = accessor.getFirstNativeHeader("Authorization");

              if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
                throw new MessageDeliveryException("Missing Authorization header");
              }

              String token = authHeader.substring(7);
              
              try {
                String username = jwtService.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (!jwtService.isTokenValid(token, userDetails)) {
                  throw new MessageDeliveryException("Invalid JWT token");
                }

                accessor.setUser(
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities()));
              } catch (MessageDeliveryException e) {
                throw e;
              } catch (Exception e) {
                throw new MessageDeliveryException("Invalid JWT token");
              }
              
            } else if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
              if (auth.getPrincipal() instanceof UserDetails ud) {
                try {
                  com.chatapp.domain.user.entity.User user =
                      (com.chatapp.domain.user.entity.User) ud;
                  presenceService.refresh(user.getId());
                } catch (Exception ignored) {
                }
              }
            }

            return message;
          }
        });
  }
}
