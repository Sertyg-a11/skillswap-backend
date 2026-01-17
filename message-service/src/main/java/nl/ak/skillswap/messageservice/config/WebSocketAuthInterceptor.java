package nl.ak.skillswap.messageservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.ak.skillswap.messageservice.service.UserIdResolverService;
import nl.ak.skillswap.messageservice.support.AuthenticatedUserContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final UserIdResolverService userIdResolverService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String tokenValue = authHeader.substring(7);

                try {
                    Jwt jwt = jwtDecoder.decode(tokenValue);
                    String externalId = jwt.getSubject();

                    if (externalId != null) {
                        // Resolve Keycloak external ID to database UUID
                        AuthenticatedUserContext ctx = userIdResolverService.resolve(externalId, authHeader);

                        // Create authentication with database UUID as principal
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                ctx.databaseId(),
                                ctx.externalId(), // Store external ID as credentials for reference
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                        accessor.setUser(auth);
                        log.debug("WebSocket authenticated for user: {} (database ID: {})", externalId, ctx.databaseId());
                    }
                } catch (JwtException e) {
                    log.warn("Invalid JWT token in WebSocket connection: {}", e.getMessage());
                    throw new IllegalArgumentException("Invalid authentication token");
                } catch (UserIdResolverService.UserResolutionException e) {
                    log.warn("Failed to resolve user for WebSocket: {}", e.getMessage());
                    throw new IllegalArgumentException("User not found");
                } catch (Exception e) {
                    log.warn("WebSocket authentication error: {}", e.getMessage());
                    throw new IllegalArgumentException("Authentication failed");
                }
            } else {
                log.warn("No Authorization header in WebSocket CONNECT");
                throw new IllegalArgumentException("Authentication required");
            }
        }

        return message;
    }
}
