package com.chatsocial.service;

import com.chatsocial.config.WebSocketAuthInterceptor;
import com.chatsocial.dto.presence.FriendPresenceResponse;
import com.chatsocial.dto.presence.PresenceResponse;
import com.chatsocial.entity.User;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.repository.FriendshipRepository;
import com.chatsocial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final WebSocketAuthInterceptor authentication;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceResponse getOnlineFriends(String userId) {
        // Lấy các tài khoản còn kết nối và có phiên hợp lệ.
        Set<String> onlineUserIds = authentication.getOnlineUserIds();
        return createSnapshot(userId, onlineUserIds);
    }

    private PresenceResponse createSnapshot(String userId, Set<String> onlineUserIds) {
        // Query hiện có đã lọc:
        // - Phải là bạn bè.
        // - Tài khoản đang hoạt động.
        // - Không chặn nhau.
        //
        // Vì vậy không lấy lastSeen của người lạ.
        var friendIds = friendshipRepository.findActiveFriendIds(userId, UserStatus.ACTIVE);

        // Không có bạn bè thì trả danh sách rỗng.
        if (friendIds.isEmpty()) {
            return new PresenceResponse(
                    "PRESENCE_SNAPSHOT",
                    java.util.List.of(),
                    java.util.List.of()
            );
        }

        // Đọc thông tin bạn bè bằng một lần truy vấn.
        // Chỉ đưa các trường cần thiết vào DTO trả ra ngoài.
        var friends = userRepository.findAllById(friendIds)
                .stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .sorted(Comparator.comparing(User::getId))
                .map(user -> {
                    boolean online = onlineUserIds.contains(user.getId());

                    return new FriendPresenceResponse(
                            user.getId(),
                            online,

                            // Đang online thì FE chỉ hiện chấm xanh.
                            // Không cần trả mốc lastSeen cũ.
                            online ? null : user.getLastSeenAt()
                    );
                })
                .toList();

        // Giữ onlineUserIds cho FE chấm xanh đang dùng.
        var onlineFriendIds = friends.stream()
                .filter(FriendPresenceResponse::online)
                .map(FriendPresenceResponse::userId)
                .toList();

        return new PresenceResponse(
                "PRESENCE_SNAPSHOT",
                onlineFriendIds,
                friends
        );
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void broadcastPresence() {
        // Chỉ kiểm tra các phiên một lần trong mỗi lượt cập nhật.
        Set<String> onlineUserIds = authentication.getOnlineUserIds();

        if (!onlineUserIds.isEmpty()) {
            try {
                // Lưu mốc gần nhất BE còn thấy các tài khoản online.
                //
                // Mốc này là dự phòng khi:
                // - BE bị tắt đột ngột.
                // - JWT/phiên hết hiệu lực trước khi socket đóng.
                //
                // Khi vẫn online, createSnapshot() trả lastSeenAt=null.
                userRepository.updateLastSeen(
                        onlineUserIds,
                        Instant.now()
                );
            } catch (RuntimeException exception) {
                log.warn("Could not update presence timestamps", exception);
            }
        }

        for (String userId : onlineUserIds) {
            try {
                PresenceResponse snapshot = createSnapshot(
                        userId,
                        onlineUserIds
                );

                // Gửi riêng danh sách trạng thái bạn bè
                // cho từng tài khoản đang kết nối.
                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/presence",
                        snapshot
                );
            } catch (RuntimeException exception) {
                // Lỗi của một người không làm dừng cả lượt cập nhật.
                log.warn(
                        "Could not update presence for user {}",
                        userId,
                        exception
                );
            }
        }
    }

}
