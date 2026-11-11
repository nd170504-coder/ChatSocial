package com.chatsocial.repository;

import com.chatsocial.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    Optional<Message> findByIdAndConversationId(String id, String conversationId);

    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(String conversationId, String senderId, String clientMessageId);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDescIdDesc(String conversationId);

    Slice<Message> findByConversationId(String conversationId, Pageable pageable);

    @Query("""
            select m from Message m
            where m.conversation.id = :conversationId
              and (
                  m.createdAt < :beforeTime
                  or (m.createdAt = :beforeTime and m.id < :beforeId)
              )
            """)
    Slice<Message> findBefore(
            @Param("conversationId") String conversationId,
            @Param("beforeTime") Instant beforeTime,
            @Param("beforeId") String beforeId,
            Pageable pageable
    );

    @Query("""
            select count(m) from Message m
            where m.conversation.id = :conversationId
              and m.sender.id <> :userId
              and m.deletedAt is null
              and not exists (
                  select r.id from MessageRead r
                  where r.message.id = m.id
                    and r.user.id = :userId
              )
            """)
    long countUnread(
            @Param("conversationId") String conversationId,
            @Param("userId") String userId
    );

    @Query("""
            select m from Message m
            where m.conversation.id = :conversationId
              and m.sender.id <> :userId
              and m.deletedAt is null
              and (
                  m.createdAt < :throughTime
                  or (m.createdAt = :throughTime and m.id <= :throughId)
              )
              and not exists (
                  select r.id from MessageRead r
                  where r.message.id = m.id
                    and r.user.id = :userId
              )
            """)
    List<Message> findUnreadThrough(
            @Param("conversationId") String conversationId,
            @Param("userId") String userId,
            @Param("throughTime") Instant throughTime,
            @Param("throughId") String throughId,
            Pageable pageable
    );

}
