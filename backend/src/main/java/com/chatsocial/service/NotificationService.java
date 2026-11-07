package com.chatsocial.service;

import com.chatsocial.dto.notification.NotificationPageResponse;
import com.chatsocial.dto.notification.NotificationResponse;
import com.chatsocial.dto.notification.UnreadCountResponse;
import com.chatsocial.entity.Notification;
import com.chatsocial.entity.Post;
import com.chatsocial.entity.User;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.NotificationType;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.event.NotificationCreated;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.NotificationRepository;
import com.chatsocial.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;
    private final UserProfileRepository profiles;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public NotificationPageResponse list(String userId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page phải từ 0 trở lên, size từ 1 đến 50");
        }

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );

        var result = notifications.findForRecipient(userId, pageable);

        var items = result.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new NotificationPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(String userId) {
        return new UnreadCountResponse(
                notifications.countByRecipientIdAndReadAtIsNull(userId)
        );
    }

    @Transactional
    public NotificationResponse markRead(String userId, String id) {
        Notification notification = notifications
                .findOwnForUpdate(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }

        return toResponse(notification);
    }

    @Transactional
    public UnreadCountResponse markAllRead(String userId) {
        notifications.markAllRead(userId, Instant.now());

        return new UnreadCountResponse(notifications.countByRecipientIdAndReadAtIsNull(userId));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void create(User recipient, User actor, Post post, NotificationType type) {
        if (recipient.getId().equals(actor.getId())) {
            return;
        }

        if (recipient.getStatus() != UserStatus.ACTIVE) {
            return;
        }

        Notification notification = new Notification(null, recipient, actor, post, type, null, null);

        notifications.saveAndFlush(notification);

        events.publishEvent(new NotificationCreated(
                recipient.getId(),
                toResponse(notification)
        ));
    }

    private NotificationResponse toResponse(Notification notification) {
        User actor = notification.getActor();

        UserProfile profile = profiles.findByUserId(actor.getId())
                .orElse(null);

        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                actor.getId(),
                profile == null
                        ? actor.getUsername()
                        : profile.getFullName(),
                profile == null ? null : profile.getAvatarUrl(),
                notification.getPost() == null
                        ? null
                        : notification.getPost().getId(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }

}
