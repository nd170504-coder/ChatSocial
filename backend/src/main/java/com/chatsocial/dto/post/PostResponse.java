package com.chatsocial.dto.post;

import com.chatsocial.entity.Post;
import com.chatsocial.entity.PostMedia;
import com.chatsocial.enums.PostPrivacy;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class PostResponse {

    private String id;
    private String userId;
    private String content;
    private PostPrivacy privacy;
    private Instant createdAt;
    private Instant updatedAt;
    private List<PostMediaResponse> media;

    public static PostResponse from(Post post, List<PostMedia> media) {
        return new PostResponse(
                post.getId(),
                post.getUser().getId(),
                post.getContent(),
                post.getPrivacy(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                media.stream()
                        .map(PostMediaResponse::from)
                        .toList()
        );
    }

}
