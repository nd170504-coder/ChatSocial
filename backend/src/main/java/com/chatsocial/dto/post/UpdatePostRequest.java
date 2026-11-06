package com.chatsocial.dto.post;

import com.chatsocial.enums.PostPrivacy;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class UpdatePostRequest {

    @Size(max = 5000, message = "Nội dung bài viết tối đa 5000 ký tự")
    private String content;

    @JsonAlias("privacy")
    private PostPrivacy postPrivacy;

    @JsonIgnore
    private boolean contentProvided;
    @JsonIgnore
    private boolean privacyProvided;

    public void setContent(String content) {
        this.content = content;
        this.contentProvided = true;
    }

    public void setPostPrivacy(PostPrivacy postPrivacy) {
        this.postPrivacy = postPrivacy;
        this.privacyProvided = true;
    }

}
