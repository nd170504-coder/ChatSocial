package com.chatsocial.repository;

import com.chatsocial.entity.Friendship;
import com.chatsocial.entity.User;
import com.chatsocial.enums.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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

    @Query("""
        select u
        from User u
        where u.status = :status
          and exists (
              select f.id
              from Friendship f
              where (f.user.id = :userId and f.friend.id = u.id)
                 or (f.friend.id = :userId and f.user.id = u.id)
          )
        """)
    Slice<User> findFriends(
            @Param("userId") String userId,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    @Query("""
        select count(u)
        from User u
        where u.status = :status
          and exists (
              select f.id
              from Friendship f
              where (f.user.id = :userId and f.friend.id = u.id)
                 or (f.friend.id = :userId and f.user.id = u.id)
          )
        """)
    long countActiveFriends(
            @Param("userId") String userId,
            @Param("status") UserStatus status
    );

}
