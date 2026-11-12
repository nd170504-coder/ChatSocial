package com.chatsocial.service;

import com.chatsocial.event.NotificationCreated;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSocketListener {

    private final SimpMessagingTemplate messaging;
    private final BlockPolicy blockPolicy;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void created(NotificationCreated event) {
        if (blockPolicy.between(event.recipientId(), event.notification().actorId())) return;
        try {
            messaging.convertAndSendToUser(
                    event.recipientId(),
                    "/queue/notifications",
                    event.notification()
            );
        } catch (RuntimeException exception) {
            log.warn("Could not push notification {}", event.notification().id());
        }
    }

}
