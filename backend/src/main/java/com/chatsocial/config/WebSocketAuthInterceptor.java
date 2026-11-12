package com.chatsocial.config;

import com.chatsocial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor{

    private final JwtDecoder jwtDecoder;
    private final ActiveUserJwtConverter userConverter;
    private final UserRepository userRepository;

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
            rememberLastSeen(authentication.getName());

            return message;
        }

        if (command == StompCommand.DISCONNECT) {
            removeSession(sessionId);
            return message;
        }

        JwtAuthenticationToken authentication = sessions.get(sessionId);

        if (authentication == null) {
            throw new BadCredentialsException("WebSocket is not authenticated");
        }

        JwtAuthenticationToken currentAuthentication = authenticate(authentication.getToken().getTokenValue());

        accessor.setUser(currentAuthentication);

        if (command == StompCommand.SEND) {
            if (!"/app/chat/typing".equals(accessor.getDestination())) {
                throw new AccessDeniedException("Send destination is not allowed");
            }
            return message;
        }

        if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();

            boolean allowed =
                    "/user/queue/notifications".equals(destination)
                            || "/user/queue/chat".equals(destination)
                            || "/user/queue/presence".equals(destination)
                            || "/user/queue/typing".equals(destination);
            if (!allowed) {
                throw new AccessDeniedException(
                        "Subscription is not allowed"
                );
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
        removeSession(event.getSessionId());
    }

    private void removeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        // Xóa đúng kết nối vừa ngắt và lấy tài khoản của kết nối đó.
        JwtAuthenticationToken removed = sessions.remove(sessionId);

        // DISCONNECT và SessionDisconnectEvent có thể cùng xảy ra.
        // Chỉ xử lý lần đầu, tránh ghi lại thời gian hai lần.
        if (removed == null) {
            return;
        }

        String userId = removed.getName();

        // getOnlineUserIds() hiện tại đã gộp các phiên theo tài khoản.
        //
        // Nếu tab/thiết bị khác vẫn còn kết nối hợp lệ,
        // tài khoản vẫn online nên chưa ghi nhận lần ngắt cuối.
        if (getOnlineUserIds().contains(userId)) {
            return;
        }

        // Đây là kết nối hợp lệ cuối cùng của tài khoản.
        rememberLastSeen(userId);
    }

    private void rememberLastSeen(String userId) {
        try {
            userRepository.updateLastSeen(Set.of(userId), Instant.now());
        } catch (RuntimeException exception) {
            // Lỗi lưu lastSeen không được làm hỏng quá trình
            // mở/đóng kết nối WebSocket.
            log.warn("Could not update last seen for user {}", userId, exception);
        }
    }

    public Set<String> getOnlineUserIds() {
        Set<String> onlineUserIds = new HashSet<>();

        for (JwtAuthenticationToken session : sessions.values()) {
            try {
                // Kiểm tra lại JWT, tài khoản và phiên đăng nhập.
                JwtAuthenticationToken authentication = authenticate(
                        session.getToken().getTokenValue()
                );

                onlineUserIds.add(authentication.getName());
            } catch (RuntimeException exception) {
                // Phiên hết hạn hoặc bị thu hồi không được tính online.
            }
        }

        return Set.copyOf(onlineUserIds);
    }

}
