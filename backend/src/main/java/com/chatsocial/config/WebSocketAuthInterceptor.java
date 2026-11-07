package com.chatsocial.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor{

    private final JwtDecoder jwtDecoder;
    private final ActiveUserJwtConverter userConverter;

    private final Map<String, JwtAuthenticationToken> sessions = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        String sessionId = accessor.getSessionId();

        if (sessionId == null) {
            throw new BadCredentialsException("Missing WebSocket session");
        }

        if (command == StompCommand.CONNECT) {
            var values = accessor.getNativeHeader("Authorization");

            if (values == null || values.size() != 1) {
                throw new BadCredentialsException("Missing access token");
            }

            String header = values.get(0);

            if (header == null || !header.startsWith("Bearer ")) {
                throw new BadCredentialsException("Invalid access token");
            }

            JwtAuthenticationToken authentication = authenticate(header.substring(7).trim());

            accessor.setUser(authentication);
            sessions.put(sessionId, authentication);

            return message;
        }

        if (command == StompCommand.DISCONNECT) {
            sessions.remove(sessionId);
            return message;
        }

        JwtAuthenticationToken authentication = sessions.get(sessionId);

        if (authentication == null) {
            throw new BadCredentialsException("WebSocket is not authenticated");
        }

        authenticate(authentication.getToken().getTokenValue());

        if (command == StompCommand.SUBSCRIBE) {
            if (!"/user/queue/notifications".equals(accessor.getDestination())) {
                throw new AccessDeniedException("Subscription is not allowed");
            }

            return message;
        }

        if (command == StompCommand.UNSUBSCRIBE || command == null) {
            return message;
        }

        throw new AccessDeniedException("Client command is not allowed");
    }

    public boolean canDeliver(Message<?> message) {
        String sessionId = SimpMessageHeaderAccessor.wrap(message).getSessionId();

        JwtAuthenticationToken authentication = sessionId == null
                ? null
                : sessions.get(sessionId);

        if (authentication == null) {
            return false;
        }

        try {
            authenticate(authentication.getToken().getTokenValue());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private JwtAuthenticationToken authenticate(String token) {
        try {
            var jwt = jwtDecoder.decode(token);
            return (JwtAuthenticationToken) userConverter.convert(jwt);
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("Access token or session is unavailable");
        }
    }

    @EventListener
    public void disconnected(SessionDisconnectEvent event) {
        sessions.remove(event.getSessionId());
    }

}
