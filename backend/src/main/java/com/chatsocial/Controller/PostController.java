package com.chatsocial.Controller;

import com.chatsocial.dto.ApiResponse;
import com.chatsocial.dto.post.CreatePostRequest;
import com.chatsocial.dto.post.PostResponse;
import com.chatsocial.dto.post.PostPageResponse;
import com.chatsocial.dto.post.UpdatePostRequest;
import com.chatsocial.service.PostService;
import com.chatsocial.service.SupabaseStorageService;
import com.chatsocial.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(
        value = "/api/post",
        produces = MediaType.APPLICATION_JSON_VALUE
)
@Tag(name = "Posts", description = "Quản lý bài viết")
public class PostController {

    private final PostService postService;
    private final SupabaseStorageService storage;

    @GetMapping("/feed")
    @Operation(summary = "Lấy bảng tin", description = "Bài PUBLIC và bài FRIENDS của bạn bè đang hoạt động; loại bài của chính mình, mới nhất trước.")
    public ResponseEntity<ApiResponse<PostPageResponse>> getFeed(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Feed retrieved successfully",
                postService.getFeed(SecurityUtils.getCurrentUserId(), page, size)));
    }

    @GetMapping("/public")
    @Operation(summary = "Lấy bài viết công khai", description = "Bài PUBLIC của tài khoản đang hoạt động, mới nhất trước.")
    public ResponseEntity<ApiResponse<PostPageResponse>> getPublicPosts(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Public posts retrieved successfully",
                postService.getPublicPosts(page, size)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Tạo bài viết",
            description = "Part post là JSON, files là ảnh (tối đa 5 ảnh, 5 MB/ảnh). "
                    + "Phải có chữ hoặc ảnh. Privacy mặc định là FRIENDS."
    )
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestPart("post") CreatePostRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        String userId = SecurityUtils.getCurrentUserId();

        PostResponse response = postService.createPost(userId, request, files);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Post created successfully", response));
    }

    @GetMapping(value = "/{postId}/media/{mediaId}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Đọc ảnh bài viết", description = "Kiểm tra quyền xem bài trước khi trả ảnh.")
    public ResponseEntity<byte[]> getMedia(@PathVariable("postId") String postId,
                                           @PathVariable("mediaId") String mediaId) {
        String path = postService.getVisibleMediaPath(SecurityUtils.getCurrentUserId(), postId, mediaId);
        var image = storage.downloadPostImage(path);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }

    @GetMapping("/{postId}")
    @Operation(
            summary = "Xem chi tiết bài viết",
            description = "Kiểm tra quyền PUBLIC, FRIENDS hoặc PRIVATE. "
                    + "Trả 404 nếu bài không tồn tại hoặc không có quyền xem."
    )
    public ResponseEntity<ApiResponse<PostResponse>> getPostById(@PathVariable("postId") String postId) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        PostResponse response = postService.getPostById(currentUserId, postId);

        return ResponseEntity.ok().body(ApiResponse.success("Post retrieved successfully", response));
    }

    @PatchMapping(
            value = "/{postId}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "Sửa bài viết",
            description = "Chỉ chủ bài được sửa nội dung hoặc quyền riêng tư. "
                    + "Trường không gửi lên được giữ nguyên."
    )
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(@PathVariable("postId") String postId, @Valid @RequestBody UpdatePostRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        PostResponse response = postService.updatePost(currentUserId, postId, request);

        return ResponseEntity.ok().body(ApiResponse.success("Post updated successfully", response));
    }

    @DeleteMapping("/{postId}")
    @Operation(
            summary = "Xóa bài viết",
            description = "Chỉ chủ bài được xóa. "
                    + "Xóa vĩnh viễn bài viết và dữ liệu liên quan trong database."
    )
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable("postId") String postId) {
        String currentUserId = SecurityUtils.getCurrentUserId();

        postService.deletePost(currentUserId, postId);

        return ResponseEntity.ok().body(ApiResponse.success("Post deleted successfully", null));
    }

}
