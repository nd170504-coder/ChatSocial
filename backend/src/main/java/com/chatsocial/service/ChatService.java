package com.chatsocial.service;

import com.chatsocial.dto.chat.*;
import com.chatsocial.entity.*;
import com.chatsocial.enums.MediaType;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.event.ChatDelivery;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.*;
import com.chatsocial.utils.ProfileImageValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ConversationRepository conversations;
    private final ConversationMemberRepository members;
    private final MessageRepository messages;
    private final MessageReadRepository reads;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final FriendshipRepository friendships;
    private final ApplicationEventPublisher events;
    private final MessageMediaRepository messageMediaRepository;
    private final SupabaseStorageService storageService;

    @Transactional
    public ConversationResponse openDirect(String currentUserId, OpenDirectRequest request) {
        String otherUserId = request.userId();

        if (currentUserId.equals(otherUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể chat với chính mình");
        }

        // Cùng thứ tự khóa với gửi lời mời và hủy kết bạn.
        lockUsers(List.of(currentUserId, otherUserId));

        String key = pairKey(currentUserId, otherUserId);
        Conversation existing = conversations.findByDirectPairKey(key)
                .orElse(null);

        // Cuộc trò chuyện cũ vẫn mở được sau khi hủy kết bạn.
        if (existing != null) {
            return toConversation(context(currentUserId, existing.getId(), false));
        }

        User me = users.findById(currentUserId)
                .orElseThrow(this::notFound);

        User other = users.findById(otherUserId)
                .orElseThrow(this::notFound);

        if (me.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn hoạt động");
        }

        if (other.getStatus() != UserStatus.ACTIVE) {
            throw notFound();
        }

        if (!friendships.areFriends(currentUserId, otherUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ bạn bè mới có thể bắt đầu trò chuyện");
        }

        Conversation conversation = conversations.saveAndFlush(new Conversation(key));

        members.saveAll(List.of(
                new ConversationMember(conversation, me),
                new ConversationMember(conversation, other)
        ));
        members.flush();

        return toConversation(context(currentUserId, conversation.getId(), false));
    }

    @Transactional(readOnly = true)
    public ConversationPageResponse list(String currentUserId, int page, int size) {
        validatePage(page, size);

        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))
        );

        var result = conversations.findMine(currentUserId, pageable);

        var items = result.getContent().stream()
                .map(conversation -> toConversation(
                        context(currentUserId, conversation.getId(), false)))
                .toList();

        return new ConversationPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(String currentUserId, String conversationId) {
        return toConversation(context(currentUserId, conversationId, false));
    }

    @Transactional(readOnly = true)
    public MessagePageResponse history(String currentUserId, String conversationId, String before, int size) {
        Context ctx = context(currentUserId, conversationId, false);
        validatePage(0, size);

        var pageable = PageRequest.of(
                0,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );

        var result = before == null
                ? messages.findByConversationId(conversationId, pageable)
                : messages.findBefore(conversationId, anchor(conversationId, before).getCreatedAt(), before, pageable);

        var items = result.getContent().stream()
                .map(message -> toMessage(message, ctx))
                .toList();

        String nextBefore = result.hasNext() && !items.isEmpty()
                ? items.get(items.size() - 1).id()
                : null;

        return new MessagePageResponse(
                items,
                result.hasNext(),
                nextBefore
        );
    }

    @Transactional
    public MessageResponse send(String currentUserId, String conversationId, SendMessageRequest request) {
        Context ctx = context(currentUserId, conversationId, true);

        String content = request.content().strip();

        if (content.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tin nhắn không được để trống");
        }

        String clientId = request.clientMessageId().toString();

        Message existing = messages
                .findByConversationIdAndSenderIdAndClientMessageId(
                        conversationId,
                        currentUserId,
                        clientId
                )
                .orElse(null);

        if (existing != null) {
            if (existing.getDeletedAt() != null
                    || messageMediaRepository.existsByMessageId(existing.getId())
                    || !Objects.equals(existing.getContent(), content)) {
                throw new ApiException(HttpStatus.CONFLICT, "clientMessageId đã được sử dụng cho tin khác");
            }

            return toMessage(existing, ctx);
        }

        if (!ctx.canSend()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Hai người hiện không thể gửi tin cho nhau");
        }

        Message message = messages.saveAndFlush(
                new Message(ctx.conversation(), ctx.me(), content, clientId)
        );

        ctx.conversation().touch();
        conversations.flush();

        MessageResponse response = toMessage(message, ctx);

        broadcast(ctx, new ChatEvent(
                "MESSAGE_CREATED",
                conversationId,
                response,
                null
        ));

        return response;
    }

    @Transactional
    public MessageResponse recall(String currentUserId, String conversationId, String messageId) {
        Context ctx = context(currentUserId, conversationId, true);
        Message message = anchor(conversationId, messageId);
        if (!message.getSender().getId().equals(currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ được thu hồi tin nhắn của mình");
        }
        // Retry is harmless and must not send duplicate socket events.
        if (message.getDeletedAt() != null) return toMessage(message, ctx);
        message.setDeletedAt(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        messages.flush();
        MessageResponse response = toMessage(message, ctx);
        broadcast(ctx, new ChatEvent("MESSAGE_RECALLED", conversationId, response, null));
        return response;
    }

    @Transactional
    public ReadResponse markRead(String currentUserId, String conversationId, ReadRequest request) {
        Context ctx = context(currentUserId, conversationId, true);

        Message through = anchor(conversationId, request.lastReadMessageId());

        boolean changed = false;

        // Xử lý theo lô để không tải toàn bộ lịch sử vào bộ nhớ.
        while (true) {
            var batch = messages.findUnreadThrough(
                    conversationId,
                    currentUserId,
                    through.getCreatedAt(),
                    through.getId(),
                    PageRequest.of(0, 200)
            );

            if (batch.isEmpty()) {
                break;
            }

            reads.saveAll(batch.stream()
                    .map(message -> new MessageRead(message, ctx.me()))
                    .toList());

            reads.flush();
            changed = true;
        }

        var latest = reads.findLatestRead(conversationId, currentUserId, PageRequest.of(0, 1));

        Message lastRead = latest.isEmpty()
                ? null
                : latest.get(0).getMessage();

        ReadResponse response = new ReadResponse(
                conversationId,
                currentUserId,
                lastRead == null ? null : lastRead.getId(),
                lastRead == null ? null : lastRead.getCreatedAt(),
                messages.countUnread(conversationId, currentUserId)
        );

        if (changed) {
            broadcast(ctx, new ChatEvent(
                    "MESSAGES_READ",
                    conversationId,
                    null,
                    response
            ));
        }

        return response;
    }

    private Context context(String currentUserId, String conversationId, boolean forUpdate) {
        if (forUpdate) {
            // Chỉ lấy ID trước khi khóa, tránh dùng User đã đọc cũ.
            var ids = members.findUserIds(conversationId);

            if (ids.size() != 2 || !ids.contains(currentUserId)) {
                throw notFound();
            }

            lockUsers(ids);
        }

        Conversation conversation = (forUpdate
                ? conversations.findForUpdate(conversationId)
                : conversations.findById(conversationId))
                .orElseThrow(this::notFound);

        var participants = members.findMembers(conversationId);

        if (participants.size() != 2 || conversation.getDirectPairKey() == null) {
            throw notFound();
        }

        ConversationMember own = participants.stream()
                .filter(member -> member.getUser().getId()
                        .equals(currentUserId))
                .findFirst()
                .orElseThrow(this::notFound);

        if (own.getLeftAt() != null) {
            throw notFound();
        }

        ConversationMember otherMember = participants.stream()
                .filter(member -> !member.getUser().getId()
                        .equals(currentUserId))
                .findFirst()
                .orElseThrow(this::notFound);

        User me = own.getUser();
        User other = otherMember.getUser();

        if (me.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Tài khoản không còn hoạt động");
        }

        if (!conversation.getDirectPairKey().equals(pairKey(me.getId(), other.getId()))) {
            throw notFound();
        }

        boolean canSend = otherMember.getLeftAt() == null
                && other.getStatus() == UserStatus.ACTIVE
                && friendships.areFriends(me.getId(), other.getId());

        return new Context(conversation, me, other, canSend);
    }

    private ConversationResponse toConversation(Context ctx) {
        User other = ctx.other();

        UserProfile profile = profiles.findByUserId(other.getId())
                .orElse(null);

        Message last = messages.findFirstByConversationIdOrderByCreatedAtDescIdDesc(ctx.conversation().getId())
                .orElse(null);

        return new ConversationResponse(
                ctx.conversation().getId(),
                new OtherUserResponse(
                        other.getId(),
                        other.getUsername(),
                        profile == null
                                ? other.getUsername()
                                : profile.getFullName(),
                        profile == null ? null : profile.getAvatarUrl()
                ),
                last == null
                        ? null
                        : toMessage(last, ctx),
                messages.countUnread(ctx.conversation().getId(), ctx.me().getId()),
                ctx.canSend(),
                ctx.conversation().getUpdatedAt()
        );
    }

    @Transactional
    public MessageResponse sendImage(String currentUserId, String conversationId, UUID clientMessageId, MultipartFile file) {
        Context context = context(currentUserId, conversationId, true);

        var image = ProfileImageValidator.validate(file);

        String clientId = clientMessageId.toString();

        Message existing = messages.findByConversationIdAndSenderIdAndClientMessageId(conversationId, currentUserId, clientId)
                .orElse(null);

        if (existing != null) {
            var media = messageMediaRepository.findByMessageIdOrderByIdAsc(existing.getId());

            boolean imageMessage = existing.getDeletedAt() == null
                    && existing.getContent() == null
                    && media.size() == 1
                    && media.get(0).getMediaType() == MediaType.IMAGE;

            if (!imageMessage) {
                throw clientMessageConflict();
            }

            var storedImage = storageService.downloadPostImage(media.get(0).getMediaUrl());

            if (!Arrays.equals(image.bytes(), storedImage.bytes())) {
                throw clientMessageConflict();
            }

            return toMessage(existing, context);
        }

        if (!context.canSend()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Hai người hiện không thể gửi tin cho nhau");
        }

        String path = storageService.newPostPath(currentUserId, image);

        cleanupImageOnRollback(path);
        storageService.uploadPostImage(path, image);

        Message message = messages.saveAndFlush(
                new Message(
                        context.conversation,
                        context.me,
                        null,
                        clientId
                )
        );
        messageMediaRepository.saveAndFlush(new MessageMedia(message, path));

        context.conversation.touch();
        conversations.flush();

        MessageResponse response = toMessage(message, context);

        broadcast(context, new ChatEvent(
                "MESSAGE_CREATED",
                conversationId,
                response,
                null
        ));
        return response;
    }

    @Transactional(readOnly = true)
    public SupabaseStorageService.DownloadedImage getImage(String currentUserId, String conversationId, String messageId, String mediaId) {
        context(currentUserId, conversationId, false);

        Message message = anchor(conversationId, messageId);

        if (message.getDeletedAt() != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh");
        }

        MessageMedia messageMedia = messageMediaRepository.findByIdAndMessageId(mediaId, messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"));

        if (messageMedia.getMediaType() != MediaType.IMAGE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh");
        }

        return storageService.downloadPostImage(messageMedia.getMediaUrl());
    }

    private ApiException clientMessageConflict() {
        return new ApiException(HttpStatus.CONFLICT, "clientMessageId đã được sử dụng cho tin khác");
    }

    private void cleanupImageOnRollback(String path) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            try {
                                storageService.deletePostImage(path);
                            } catch (RuntimeException exception) {
                                log.warn("Could not clean up chat image {}", path, exception);
                            }
                        } else if (status == STATUS_UNKNOWN) {
                            log.warn("Unknown chat transaction outcome for image {}", path);
                        }
                    }
                }
        );
    }

    private MessageResponse toMessage(Message message, Context context) {
        if (message.getDeletedAt() != null) {
            return new MessageResponse(message.getId(), message.getConversation().getId(),
                    message.getSender().getId(), null, message.getClientMessageId(),
                    message.getCreatedAt(), false, List.of(), message.getDeletedAt());
        }
        String recipientId = message.getSender().getId()
                .equals(context.me().getId())
                ? context.other().getId()
                : context.me().getId();

        boolean read = reads.existsById(new MessageReadId(message.getId(), recipientId));

        var media = messageMediaRepository.findByMessageIdOrderByIdAsc(message.getId())
                .stream()
                .map(item -> new MessageMediaResponse(
                        item.getId(),
                        "/api/conversations/"
                                + message.getConversation().getId()
                                + "/messages/"
                                + message.getId()
                                + "/media/"
                                + item.getId(),
                        item.getMediaType()
                ))
                .toList();

        return new MessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getContent(),
                message.getClientMessageId(),
                message.getCreatedAt(),
                read,
                media,
                null
        );
    }

    private Message anchor(String conversationId, String messageId) {
        return messages.findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Tin nhắn không thuộc cuộc trò chuyện"));
    }

    private void lockUsers(List<String> ids) {
        for (String id : ids.stream().sorted().toList()) {
            users.findForUpdateById(id).orElseThrow(this::notFound);
        }
    }

    private String pairKey(String first, String second) {
        return first.compareTo(second) < 0
                ? first + ":" + second
                : second + ":" + first;
    }

    private void broadcast(Context ctx, ChatEvent payload) {
        events.publishEvent(
                new ChatDelivery(ctx.me().getId(), payload)
        );

        events.publishEvent(
                new ChatDelivery(ctx.other().getId(), payload)
        );
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page phải từ 0 trở lên, size từ 1 đến 50");
        }
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc trò chuyện");
    }

    private record Context(Conversation conversation, User me, User other, boolean canSend) {}

}
