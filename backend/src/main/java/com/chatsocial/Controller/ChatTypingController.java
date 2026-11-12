package com.chatsocial.Controller;

import com.chatsocial.dto.chat.TypingRequest;
import com.chatsocial.exception.ApiException;
import com.chatsocial.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatTypingController {

    private final ChatService chatService;

    @MessageMapping("/chat/typing")
    public void typing(@Payload TypingRequest request, Principal principal) {
        if (principal == null || request == null) {
            return;
        }

        if (request.conversationId() == null
                || request.conversationId().isBlank()
                || request.conversationId().length() > 64
                || request.typing() == null) {
            return;
        }

        try {
            chatService.typing(principal.getName(), request.conversationId(), request.typing());
        } catch (ApiException ignored) {}
    }

}
