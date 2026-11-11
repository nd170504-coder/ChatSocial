package com.chatsocial.service;

import com.chatsocial.event.ChatDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSocketListener {

    private final SimpMessagingTemplate messaging;
    private final BlockPolicy blockPolicy;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(ChatDelivery event) {
        var payload = event.payload();
        String actorId = "MESSAGE_CREATED".equals(payload.type()) ? payload.message().senderId()
                : "MESSAGES_READ".equals(payload.type()) ? payload.read().readerId() : null;
        if (actorId != null && blockPolicy.between(event.recipientId(), actorId)) return;
        try {
            messaging.convertAndSendToUser(
                    event.recipientId(),
                    "/queue/chat",
                    event.payload()
            );
        } catch (RuntimeException exception) {
            log.warn("Could not push chat event for conversation {}", event.payload().conversationId());
        }
    }

}
