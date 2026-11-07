package com.chatsocial.repository;

import com.chatsocial.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    @Query("""
            select n from Notification n
            join fetch n.actor
            where n.recipient.id = :userId
            """)
    Slice<Notification> findForRecipient(@Param("userId") String userId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from Notification n
            where n.id = :id
              and n.recipient.id = :userId
            """)
    Optional<Notification> findOwnForUpdate(@Param("id") String id, @Param("userId") String userId);

    @Modifying
    @Query("""
            update Notification n
            set n.readAt = :now
            where n.recipient.id = :userId
              and n.readAt is null
            """)
    int markAllRead(@Param("userId") String userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.post.id = :postId")
    void deleteAllByPostId(@Param("postId") String postId);

}
