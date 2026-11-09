package com.chatsocial.repository;

import com.chatsocial.entity.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    Optional<Conversation> findByDirectPairKey(String directPairKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findForUpdate(@Param("id") String id);

    @Query("""
            select c
            from Conversation c
            where c.directPairKey is not null
              and exists (
                  select member.id from ConversationMember member
                  where member.conversation = c
                    and member.user.id = :userId
                    and member.leftAt is null
              )
            """)
    Slice<Conversation> findMine(
            @Param("userId") String userId,
            Pageable pageable
    );

}
