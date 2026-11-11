package com.chatsocial.service;

import com.chatsocial.dto.block.*;
import com.chatsocial.entity.User;
import com.chatsocial.entity.UserBlock;
import com.chatsocial.enums.FriendshipStatus;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.event.BlockChanged;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class BlockService {
    private final UserBlockRepository blocks;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final FriendshipRepository friendships;
    private final FriendRequestRepository requests;
    private final ConversationRepository conversations;
    private final BlockPolicy policy;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public BlockStatusResponse status(String me, String other) {
        if (!users.existsById(other)) throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng");
        return new BlockStatusResponse(other, blocks.existsByBlockerIdAndBlockedId(me, other), policy.between(me, other));
    }

    @Transactional(readOnly = true)
    public BlockPageResponse list(String me, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new ApiException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ");
        var result = blocks.findMine(me, PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        var items = result.getContent().stream().map(block -> {
            User user = block.getBlocked();
            var profile = profiles.findByUserId(user.getId()).orElse(null);
            return new BlockedUserResponse(user.getId(), user.getUsername(), profile == null ? user.getUsername() : profile.getFullName(),
                    profile == null ? null : profile.getAvatarUrl(), block.getCreatedAt());
        }).toList();
        return new BlockPageResponse(items, result.getNumber(), result.getSize(), result.hasNext());
    }

    @Transactional
    public BlockStatusResponse change(String me, String other, boolean block) {
        if (me.equals(other)) throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể chặn chính mình");
        // Same lock order as chat, accept and unfriend; also protects concurrent retries.
        for (String id : Stream.of(me, other).sorted().toList()) {
            User user = users.findForUpdateById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
            if (id.equals(me) && user.getStatus() != UserStatus.ACTIVE) throw new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn hoạt động");
        }
        boolean changed = false;
        if (block && !blocks.existsByBlockerIdAndBlockedId(me, other)) {
            blocks.saveAndFlush(new UserBlock(users.getReferenceById(me), users.getReferenceById(other)));
            friendships.deleteFriendship(me, other);
            requests.findByPairKey(pairKey(me, other)).filter(request -> request.getStatus() == FriendshipStatus.PENDING)
                    .ifPresent(request -> { requests.delete(request); requests.flush(); });
            changed = true;
        } else if (!block) {
            changed = blocks.deleteByBlockerIdAndBlockedId(me, other) > 0;
            blocks.flush();
        }
        if (changed) {
            String conversationId = conversations.findByDirectPairKey(pairKey(me, other)).map(value -> value.getId()).orElse("relationship");
            events.publishEvent(new BlockChanged(me, other, conversationId));
        }
        return status(me, other);
    }

    private String pairKey(String a, String b) { return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a; }
}
