package com.chatsocial.dto.post;

import com.chatsocial.entity.PostMedia;
import com.chatsocial.enums.MediaType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostMediaResponse {
    private String id;
    private String url;
    private MediaType mediaType;
    private Integer displayOrder;

    public static PostMediaResponse from(PostMedia media) {
        return new PostMediaResponse(media.getId(),
                "/api/post/" + media.getPost().getId() + "/media/" + media.getId(),
                media.getMediaType(), media.getDisplayOrder());
    }
}
