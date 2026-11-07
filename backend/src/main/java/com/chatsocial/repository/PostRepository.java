package com.chatsocial.repository;

import com.chatsocial.entity.Post;
import com.chatsocial.enums.PostPrivacy;
import com.chatsocial.enums.UserStatus;
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
public interface PostRepository extends JpaRepository<Post, String> {

    @Query("""
            select p from Post p join fetch p.user author
            where author.id <> :viewerId
              and author.status = :activeStatus
              and (p.privacy = :publicPrivacy or (
                  p.privacy = :friendsPrivacy and exists (
                      select f.id from Friendship f
                      where (f.user.id = :viewerId and f.friend.id = author.id)
                         or (f.friend.id = :viewerId and f.user.id = author.id)
                  )
              ))
            """)
    Slice<Post> findFeed(@Param("viewerId") String viewerId,
                         @Param("activeStatus") UserStatus activeStatus,
                         @Param("publicPrivacy") PostPrivacy publicPrivacy,
                         @Param("friendsPrivacy") PostPrivacy friendsPrivacy, Pageable pageable);

    @Query("""
            select p from Post p join fetch p.user author
            where p.privacy = :privacy and author.status = :status
            """)
    Slice<Post> findPublicPosts(@Param("privacy") PostPrivacy privacy,
                               @Param("status") UserStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Post p where p.id = :postId")
    Optional<Post> findForUpdateById(@Param("postId") String postId);

    @Query("""
        select p
        from Post p
        join p.user author
        where author.id = :authorId
          and author.status = :activeStatus
          and (
              author.id = :viewerId
              or p.privacy = :publicPrivacy
              or (
                  p.privacy = :friendsPrivacy
                  and exists (
                      select f.id
                      from Friendship f
                      where (
                          f.user.id = :viewerId
                          and f.friend.id = :authorId
                      ) or (
                          f.user.id = :authorId
                          and f.friend.id = :viewerId
                      )
                  )
              )
          )
        """)
    Slice<Post> findVisibleByAuthor(
            @Param("authorId") String authorId,
            @Param("viewerId") String viewerId,
            @Param("activeStatus") UserStatus activeStatus,
            @Param("publicPrivacy") PostPrivacy publicPrivacy,
            @Param("friendsPrivacy") PostPrivacy friendsPrivacy,
            Pageable pageable
    );

}
