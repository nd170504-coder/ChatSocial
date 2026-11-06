package com.chatsocial.repository;

import com.chatsocial.entity.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PostMediaRepository extends JpaRepository<PostMedia, String> {

    @Modifying
    @Query("delete from PostMedia pm where pm.post.id = :postId")
    void deleteAllByPostId(@Param("postId") String postId);

    List<PostMedia> findByPostIdOrderByDisplayOrderAsc(String postId);

    List<PostMedia> findByPostIdInOrderByDisplayOrderAsc(Collection<String> postIds);

    Optional<PostMedia> findByIdAndPostId(String id, String postId);

    boolean existsByPostId(String postId);

    @Query("select pm.mediaUrl from PostMedia pm where pm.post.id = :postId")
    List<String> findStoragePathsByPostId(@Param("postId") String postId);

}
