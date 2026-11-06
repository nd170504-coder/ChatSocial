package com.chatsocial.repository;

import com.chatsocial.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, String > {

    @Query("""
            select count(f)
            from Friendship f
            where (f.user.id = :userId and f.friend.id = :otherUserId)
               or (f.user.id = :otherUserId and f.friend.id = :userId)
            """)
    long countFriendship(
            @Param("userId") String userId,
            @Param("otherUserId") String otherUserId
    );

    default boolean areFriends(String userId, String otherUserId) {
        return countFriendship(userId, otherUserId) > 0;
    }

}
