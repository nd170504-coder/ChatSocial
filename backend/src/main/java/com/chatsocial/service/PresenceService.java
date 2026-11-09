package com.chatsocial.service;

import com.chatsocial.config.WebSocketAuthInterceptor;
import com.chatsocial.dto.presence.PresenceResponse;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final WebSocketAuthInterceptor authentication;
    private final FriendshipRepository friendshipRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceResponse getOnlineFriends(String userId) {
        Set<String> onlineUserIds = authentication.getOnlineUserIds();

        return createSnapshot(userId, onlineUserIds);
    }

    private PresenceResponse createSnapshot(String userId, Set<String> onlineUserIds) {
        var onlineFriendIds = friendshipRepository
                .findActiveFriendIds(userId, UserStatus.ACTIVE)
                .stream()
                .filter(onlineUserIds::contains)
                .sorted()
                .toList();

        return new PresenceResponse(
                "PRESENCE_SNAPSHOT",
                onlineFriendIds
        );
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void broadcastPresence() {
        // Chỉ kiểm tra các phiên một lần trong mỗi lượt cập nhật.
        Set<String> onlineUserIds = authentication.getOnlineUserIds();

        for (String userId : onlineUserIds) {
            try {
                PresenceResponse snapshot = createSnapshot(userId, onlineUserIds);

                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/presence",
                        snapshot
                );
            } catch (RuntimeException exception) {
                // Một lần gửi lỗi không làm dừng cập nhật cho người khác.
                log.warn("Could not update presence for user {}", userId, exception);
            }
        }
    }

}
