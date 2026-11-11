package com.chatsocial.repository;

import com.chatsocial.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface UserBlockRepository extends JpaRepository<UserBlock, String> {
    boolean existsByBlockerIdAndBlockedId(String blockerId, String blockedId);
    long deleteByBlockerIdAndBlockedId(String blockerId, String blockedId);

    @Query("""
        select count(b) from UserBlock b
        where (b.blocker.id = :first and b.blocked.id = :second)
           or (b.blocker.id = :second and b.blocked.id = :first)
        """)
    long countBetween(@Param("first") String first, @Param("second") String second);

    @Query("select b from UserBlock b join fetch b.blocked where b.blocker.id = :userId")
    Slice<UserBlock> findMine(@Param("userId") String userId, Pageable pageable);
}
