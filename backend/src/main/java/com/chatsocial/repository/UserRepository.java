package com.chatsocial.repository;

import com.chatsocial.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import com.chatsocial.enums.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, String> {
    interface StatusCount {
        UserStatus getStatus();
        long getTotal();
    }

    @Query("select u.status as status, count(u) as total from User u group by u.status")
    java.util.List<StatusCount> countByStatus();

    @Query("select u.createdAt from User u where u.createdAt >= :start and u.createdAt < :end")
    java.util.List<Instant> findRegistrationTimes(@Param("start") Instant start, @Param("end") Instant end);

    @Query("""
            select u from User u where u.role = :role
                and (lower(u.username) like :pattern escape '!'
                or lower(u.email) like :pattern escape '!')
            """)
    org.springframework.data.domain.Page<User> searchForAdmin(@Param("role") com.chatsocial.enums.UserRole role,
            @Param("pattern") String pattern, Pageable pageable);

    @Query("""
            select u from User u
            where u.status = :status and u.id <> :currentUserId
              and not exists (select blockedPair.id from UserBlock blockedPair
                where (blockedPair.blocker.id = :currentUserId and blockedPair.blocked.id = u.id)
                   or (blockedPair.blocker.id = u.id and blockedPair.blocked.id = :currentUserId))
              and ((:emailSearch = true and u.email = :query)
                or (:emailSearch = false and u.username like :pattern escape '!'))
            """)
    Slice<User> searchActiveUsers(@Param("currentUserId") String currentUserId,
                                  @Param("status") UserStatus status,
                                  @Param("emailSearch") boolean emailSearch,
                                  @Param("query") String query,
                                  @Param("pattern") String pattern, Pageable pageable);

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findForUpdateByEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdateById(@Param("id") String id);

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByRole(com.chatsocial.enums.UserRole role);
    long countByRole(com.chatsocial.enums.UserRole role);

    @Transactional
    @Modifying
    @Query("""
    update User u
    set u.lastSeenAt = :time
    where u.id in :userIds
      and (u.lastSeenAt is null or u.lastSeenAt < :time)
    """)
    int updateLastSeen(
            @Param("userIds") Collection<String> userIds,
            @Param("time") Instant time
    );
}
