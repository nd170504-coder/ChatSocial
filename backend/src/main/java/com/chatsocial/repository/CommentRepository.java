package com.chatsocial.repository;

import com.chatsocial.entity.Comment;
import com.chatsocial.enums.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CommentRepository extends JpaRepository<Comment, String> {

    @Query("""
        select count(c) from Comment c
        left join c.parentComment parent left join parent.user parentAuthor
        where c.post.user.id = :userId and c.user.id <> :userId and c.user.status = :status
          and (parent is null or parentAuthor.status = :status)
        """)
    long countReceivedComments(@Param("userId") String userId, @Param("status") UserStatus status);

    @Modifying
    @Query("delete from Comment c where c.post.id = :postId and c.parentComment is not null")
    void deleteRepliesByPostId(@Param("postId") String postId);

    @Modifying
    @Query("delete from Comment c where c.post.id = :postId")
    void deleteAllByPostId(@Param("postId") String postId);

    @Query("""
        select c from Comment c
        join fetch c.user author
        where c.post.id = :postId
          and c.parentComment is null
          and author.status = :status
        """)
    Slice<Comment> findVisibleComments(
            @Param("postId") String postId,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    @Query("""
        select count(c) from Comment c left join c.parentComment parent left join parent.user parentAuthor
        where c.post.id = :postId and c.user.status = :status
          and (parent is null or parentAuthor.status = :status)
        """)
    long countByPostIdAndUserStatus(@Param("postId") String postId, @Param("status") UserStatus status);

    @Query("""
        select c from Comment c join fetch c.user author
        where c.post.id = :postId and c.parentComment.id = :parentId and author.status = :status
        """)
    Slice<Comment> findReplies(@Param("postId") String postId, @Param("parentId") String parentId,
                               @Param("status") UserStatus status, Pageable pageable);

    long countByParentCommentIdAndUserStatus(String parentId, UserStatus status);

    @Query("select c.user.id from Comment c where c.id = :id and c.post.id = :postId")
    Optional<String> findAuthorId(@Param("id") String id, @Param("postId") String postId);

    Optional<Comment> findByIdAndPostId(String commentId, String postId);

    boolean existsByParentCommentId(String commentId);

}
