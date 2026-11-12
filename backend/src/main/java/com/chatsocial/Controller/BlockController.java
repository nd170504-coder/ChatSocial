package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.block.*;
import com.chatsocial.service.BlockService;
import com.chatsocial.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/blocks")
public class BlockController {
    private final BlockService service;
    @GetMapping
    public ApiResponse<BlockPageResponse> list(@RequestParam(name="page", defaultValue="0") int page, @RequestParam(name="size", defaultValue="20") int size) {
        return ApiResponse.success("Blocked users retrieved", service.list(SecurityUtils.getCurrentUserId(), page, size));
    }
    @GetMapping("/{userId}")
    public ApiResponse<BlockStatusResponse> status(@PathVariable("userId") String userId) {
        return ApiResponse.success("Block status retrieved", service.status(SecurityUtils.getCurrentUserId(), userId));
    }
    @PutMapping("/{userId}")
    public ApiResponse<BlockStatusResponse> block(@PathVariable("userId") String userId) {
        return ApiResponse.success("User blocked", service.change(SecurityUtils.getCurrentUserId(), userId, true));
    }
    @DeleteMapping("/{userId}")
    public ApiResponse<BlockStatusResponse> unblock(@PathVariable("userId") String userId) {
        return ApiResponse.success("User unblocked", service.change(SecurityUtils.getCurrentUserId(), userId, false));
    }
}
