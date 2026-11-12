package com.chatsocial.service;

import com.chatsocial.dto.chat.ChatEvent;
import com.chatsocial.event.BlockChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BlockSocketListener {
    private final SimpMessagingTemplate messaging;
    private final PresenceService presence;
    @TransactionalEventListener
    public void changed(BlockChanged event) {
        for (String id : List.of(event.firstUserId(), event.secondUserId())) {
            try {
                // Generic state refresh, never a notification announcing who blocked whom.
                messaging.convertAndSendToUser(id, "/queue/chat", new ChatEvent("RELATIONSHIP_CHANGED", event.conversationId(), null, null));
                messaging.convertAndSendToUser(id, "/queue/presence", presence.getOnlineFriends(id));
            } catch (RuntimeException exception) { log.warn("Could not refresh relationship for user {}", id, exception); }
        }
    }
}
