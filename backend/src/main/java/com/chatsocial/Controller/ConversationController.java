package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.chat.*;
import com.chatsocial.service.ChatService;
import com.chatsocial.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

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

    @PostMapping(
            value = "/{id}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<MessageResponse>> sendImage(
            @PathVariable("id") String conversationId,
            @RequestParam("clientMessageId") UUID clientMessageId,
            @RequestPart("file") MultipartFile file
    ) {
        var response = service.sendImage(me(), conversationId, clientMessageId, file);

        return ResponseEntity.ok(ApiResponse.success("Image sent", response));
    }

    @GetMapping(
            value = "/{id}/messages/{messageId}/media/{mediaId}",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE
    )
    public ResponseEntity<byte[]> getImage(
            @PathVariable("id") String conversationId,
            @PathVariable("messageId") String messageId,
            @PathVariable("mediaId") String mediaId
    ) {
        var image = service.getImage(me(), conversationId, messageId, mediaId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    private String me() {
        return SecurityUtils.getCurrentUserId();
    }

}
