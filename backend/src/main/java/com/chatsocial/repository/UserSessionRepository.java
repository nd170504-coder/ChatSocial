package com.chatsocial.repository;

import com.chatsocial.entity.UserSession;
import com.chatsocial.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UserSession s where s.id = :id")
    Optional<UserSession> findForUpdateById(@Param("id") String id);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.user.id = :userId and s.revokedAt is null")
    void revokeAllByUserId(@Param("userId") String userId, @Param("now") Instant now);

    @Query("""
            select (count(s) > 0) from UserSession s
            where s.id = :id and s.user.id = :userId and s.revokedAt is null
              and s.expiresAt > :now and s.user.status = :status
            """)
    boolean isActive(@Param("id") String id, @Param("userId") String userId,
                     @Param("now") Instant now, @Param("status") UserStatus status);
}
