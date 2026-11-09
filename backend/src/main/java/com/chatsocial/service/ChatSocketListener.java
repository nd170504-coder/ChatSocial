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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void deliver(ChatDelivery event) {
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
