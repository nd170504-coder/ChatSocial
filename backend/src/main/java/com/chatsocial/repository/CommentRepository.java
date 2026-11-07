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

    @Modifying
    @Query("delete from Comment c where c.post.id = :postId")
    void deleteAllByPostId(@Param("postId") String postId);

    @Query("""
        select c from Comment c
        join fetch c.user author
        where c.post.id = :postId
          and author.status = :status
        """)
    Slice<Comment> findVisibleComments(
            @Param("postId") String postId,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    long countByPostIdAndUserStatus(String postId, UserStatus status);

    Optional<Comment> findByIdAndPostId(String commentId, String postId);

    boolean existsByParentCommentId(String commentId);

}
