package com.chatsocial.repository;

import com.chatsocial.entity.MessageRead;
import com.chatsocial.entity.MessageReadId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageReadRepository extends JpaRepository<MessageRead, MessageReadId> {

    @Query("""
            select r from MessageRead r
            join fetch r.message m
            where m.conversation.id = :conversationId
              and r.user.id = :userId
            order by m.createdAt desc, m.id desc
            """)
    List<MessageRead> findLatestRead(
            @Param("conversationId") String conversationId,
            @Param("userId") String userId,
            Pageable pageable
    );

}
