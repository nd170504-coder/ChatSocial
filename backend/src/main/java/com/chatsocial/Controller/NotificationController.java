package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.notification.NotificationPageResponse;
import com.chatsocial.dto.notification.NotificationResponse;
import com.chatsocial.dto.notification.UnreadCountResponse;
import com.chatsocial.service.NotificationService;
import com.chatsocial.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/notifications",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        var response = notificationService.list(SecurityUtils.getCurrentUserId(), page, size);

        return ResponseEntity.ok(ApiResponse.success("Notifications retrieved", response));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount() {
        var response = notificationService.unreadCount(SecurityUtils.getCurrentUserId());

        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved", response));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable("id") String id) {
        var response = notificationService.markRead(SecurityUtils.getCurrentUserId(), id);

        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", response));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> markAllRead() {
        var response = notificationService.markAllRead(SecurityUtils.getCurrentUserId());

        return ResponseEntity.ok(ApiResponse.success("Notifications marked as read", response));
    }

}
