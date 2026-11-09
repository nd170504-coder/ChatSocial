package com.chatsocial.repository;

import com.chatsocial.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, String> {

    @Query("""
            select member.id.userId
            from ConversationMember member
            where member.conversation.id = :conversationId
            """)
    List<String> findUserIds(@Param("conversationId") String conversationId);

    @Query("""
            select member
            from ConversationMember member
            join fetch member.user
            where member.conversation.id = :conversationId
            """)
    List<ConversationMember> findMembers(@Param("conversationId") String conversationId);

}
