package com.chatsocial.dto.post;

import com.chatsocial.enums.PostPrivacy;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePostRequest {

    @Size(max = 5000, message = "Nội dung bài viết tối đa 5000 ký tự")
    private String content;

    @JsonAlias("privacy")
    private PostPrivacy postPrivacy;

}
