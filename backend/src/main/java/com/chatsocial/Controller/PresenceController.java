package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.presence.PresenceResponse;
import com.chatsocial.service.PresenceService;
import com.chatsocial.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/presence")
public class PresenceController {

    private final PresenceService presenceService;

    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<PresenceResponse>> getOnlineFriends() {
        String userId = SecurityUtils.getCurrentUserId();

        return ResponseEntity.ok(ApiResponse.success("Online friends retrieved", presenceService.getOnlineFriends(userId)));
    }

}
