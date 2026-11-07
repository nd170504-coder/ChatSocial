package com.chatsocial.repository;

import com.chatsocial.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, String> {

    @Modifying
    @Query("delete from PostLike pl where pl.post.id = :postId")
    void deleteAllByPostId(@Param("postId") String postId);

    long countByPostId(String postId);

    boolean existsByPostIdAndUserId(String postId, String userId);

    @Modifying
    @Query("""
        delete from PostLike pl
        where pl.post.id = :postId
          and pl.user.id = :userId
        """)
    int deleteByPostAndUser(
            @Param("postId") String postId,
            @Param("userId") String userId
    );

}
