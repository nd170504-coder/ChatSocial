package com.chatsocial.dto.post;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PostPageResponse {

    private List<PostResponse> items;
    private int page;
    private int size;
    private boolean hasNext;

}
