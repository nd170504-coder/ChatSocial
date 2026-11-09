package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.chat.*;
import com.chatsocial.service.ChatService;
import com.chatsocial.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/conversations",
        produces = MediaType.APPLICATION_JSON_VALUE
)
public class ConversationController {

    private final ChatService service;

    @PostMapping("/direct")
    public ResponseEntity<ApiResponse<ConversationResponse>> openDirect(
            @Valid @RequestBody OpenDirectRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Conversation opened",
                service.openDirect(me(), request)
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ConversationPageResponse>> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Conversations retrieved",
                service.list(me(), page, size)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ConversationResponse>> get(
            @PathVariable("id") String id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Conversation retrieved",
                service.get(me(), id)
        ));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<MessagePageResponse>> history(
            @PathVariable("id") String id,
            @RequestParam(name = "before", required = false) String before,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Messages retrieved",
                service.history(me(), id, before, size)
        ));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<MessageResponse>> send(
            @PathVariable("id") String id,
            @Valid @RequestBody SendMessageRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Message sent",
                service.send(me(), id, request)
        ));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<ReadResponse>> markRead(
            @PathVariable("id") String id,
            @Valid @RequestBody ReadRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Read status updated",
                service.markRead(me(), id, request)
        ));
    }

    private String me() {
        return SecurityUtils.getCurrentUserId();
    }

}
