package com.chatsocial.service;

import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.UserBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BlockPolicy {
    private final UserBlockRepository blocks;
    public boolean between(String first, String second) {
        return !first.equals(second) && blocks.countBetween(first, second) > 0;
    }
    public void requireVisible(String viewer, String other) {
        if (between(viewer, other)) throw new ApiException(HttpStatus.NOT_FOUND, "Người dùng hoặc nội dung không khả dụng");
    }
}
