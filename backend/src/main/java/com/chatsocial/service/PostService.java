package com.chatsocial.service;

import com.chatsocial.dto.post.CreatePostRequest;
import com.chatsocial.dto.post.PostPageResponse;
import com.chatsocial.dto.post.PostResponse;
import com.chatsocial.dto.post.UpdatePostRequest;
import com.chatsocial.entity.Post;
import com.chatsocial.entity.PostMedia;
import com.chatsocial.entity.User;
import com.chatsocial.enums.PostPrivacy;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.chatsocial.utils.ProfileImageValidator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostMediaRepository postMediaRepository;
    private final CommentRepository commentRepository;
    private final SupabaseStorageService storage;
    private final PlatformTransactionManager transactionManager;

    public PostResponse createPost(String userId, CreatePostRequest request, List<MultipartFile> files) {
        List<MultipartFile> images = files == null ? List.of() : files;
        if (images.size() > 5) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mỗi bài viết tối đa 5 ảnh");
        }
        String content = request.getContent();
        String normalizedContent = content == null || content.isBlank() ? null : content.strip();
        if (normalizedContent == null && images.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bài viết phải có nội dung hoặc ít nhất một ảnh");
        }
        var validated = images.stream().map(ProfileImageValidator::validate).toList();
        userRepository.findById(userId).filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User is unavailable"));

        List<String> paths = new ArrayList<>();
        AtomicBoolean cleanupRegistered = new AtomicBoolean(false);
        try {
            // Storage network I/O runs before the database transaction/row lock.
            for (var image : validated) {
                String path = storage.newPostPath(userId, image);
                paths.add(path);
                storage.uploadPostImage(path, image);
            }
            return Objects.requireNonNull(new TransactionTemplate(transactionManager).execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int completionStatus) {
                        if (completionStatus == STATUS_ROLLED_BACK) {
                            paths.forEach(PostService.this::deletePostImageQuietly);
                        } else if (completionStatus == STATUS_UNKNOWN) {
                            // Never delete images when the database may have committed successfully.
                            log.warn("Post commit outcome unknown; reconcile storage paths {}", paths);
                        }
                    }
                });
                cleanupRegistered.set(true);
                User user = userRepository.findForUpdateById(userId)
                        .filter(value -> value.getStatus() == UserStatus.ACTIVE)
                        .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User is unavailable"));
                Post post = postRepository.saveAndFlush(Post.builder().user(user).content(normalizedContent)
                        .privacy(request.getPostPrivacy() == null ? PostPrivacy.FRIENDS : request.getPostPrivacy())
                        .build());
                List<PostMedia> media = new ArrayList<>();
                for (int i = 0; i < paths.size(); i++) {
                    media.add(new PostMedia(post, paths.get(i), i));
                }
                List<PostMedia> savedMedia = postMediaRepository.saveAll(media);
                postMediaRepository.flush();
                return PostResponse.from(post, savedMedia);
            }));
        } catch (RuntimeException exception) {
            if (!cleanupRegistered.get()) {
                paths.forEach(this::deletePostImageQuietly);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(String currentUserId, String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(this::postNotFound);

        if (!canView(post, currentUserId)) {
            throw postNotFound();
        }

        return toResponse(post);
    }

    @Transactional
    public PostResponse updatePost(String currentUserId, String postId, UpdatePostRequest request) {
        Post post = postRepository.findForUpdateById(postId)
                .orElseThrow(this::postNotFound);

        if (!post.getUser().getId().equals(currentUserId)) {
            throw postNotFound();
        }

        if (!request.isContentProvided() && !request.isPrivacyProvided()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Phải gửi ít nhất content hoặc privacy");
        }

        if (request.isContentProvided()) {
            String content = request.getContent();

            boolean emptyContent = content == null || content.isBlank();
            if (emptyContent && !postMediaRepository.existsByPostId(postId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bài viết phải có nội dung hoặc ít nhất một ảnh");
            }

            post.setContent(emptyContent ? null : content.strip());
        }

        if (request.isPrivacyProvided()) {
            if (request.getPostPrivacy() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Quyền riêng tư không được để trống");
            }

            post.setPrivacy(request.getPostPrivacy());
        }

        postRepository.flush();

        return toResponse(post);
    }

    public void deletePost(String currentUserId, String postId) {
        List<String> paths = Objects.requireNonNull(new TransactionTemplate(transactionManager).execute(status -> {
            Post post = postRepository.findForUpdateById(postId).orElseThrow(this::postNotFound);
            if (!post.getUser().getId().equals(currentUserId)) {
                throw postNotFound();
            }
            // Scalar projection avoids keeping bulk-deleted media managed while removing their parent.
            List<String> mediaPaths = postMediaRepository.findStoragePathsByPostId(postId);
            commentRepository.deleteAllByPostId(postId);
            postLikeRepository.deleteAllByPostId(postId);
            postMediaRepository.deleteAllByPostId(postId);
            postRepository.delete(post);
            postRepository.flush();
            return mediaPaths;
        }));
        paths.forEach(this::deletePostImageQuietly);
    }

    @Transactional(readOnly = true)
    public PostPageResponse getMyPostByUser(String currentUserId, String authorId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "page phải từ 0 trở lên và size phải từ 1 đến 50");
        }

        userRepository.findById(authorId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        Sort sort = Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        );

        PageRequest pageable = PageRequest.of(page, size, sort);

        Slice<Post> posts = postRepository.findVisibleByAuthor(
                authorId,
                currentUserId,
                UserStatus.ACTIVE,
                PostPrivacy.PUBLIC,
                PostPrivacy.FRIENDS,
                pageable
        );

        return new PostPageResponse(
                toResponses(posts.getContent()),
                posts.getNumber(),
                posts.getSize(),
                posts.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public String getVisibleMediaPath(String currentUserId, String postId, String mediaId) {
        Post post = postRepository.findById(postId).orElseThrow(this::postNotFound);
        if (!canView(post, currentUserId)) {
            throw postNotFound();
        }
        return postMediaRepository.findByIdAndPostId(mediaId, postId)
                .map(PostMedia::getMediaUrl)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Post image not found"));
    }

    private PostResponse toResponse(Post post) {
        return PostResponse.from(post, postMediaRepository.findByPostIdOrderByDisplayOrderAsc(post.getId()));
    }

    private List<PostResponse> toResponses(List<Post> posts) {
        if (posts.isEmpty()) return List.of();
        List<String> ids = posts.stream().map(Post::getId).toList();
        Map<String, List<PostMedia>> mediaByPost = postMediaRepository.findByPostIdInOrderByDisplayOrderAsc(ids)
                .stream().collect(Collectors.groupingBy(media -> media.getPost().getId()));
        return posts.stream().map(post -> PostResponse.from(post,
                mediaByPost.getOrDefault(post.getId(), List.of()))).toList();
    }

    private void deletePostImageQuietly(String path) {
        try {
            storage.deletePostImage(path);
        } catch (RuntimeException exception) {
            log.warn("Could not clean up post image at {}; storage cleanup is required", path, exception);
        }
    }

    private boolean canView(Post post, String currentUserId) {
        User author = post.getUser();

        if (author.getStatus() != UserStatus.ACTIVE) {
            return false;
        }

        if (author.getId().equals(currentUserId)) {
            return true;
        }

        return switch (post.getPrivacy()) {
            case PUBLIC -> true;
            case FRIENDS -> friendshipRepository.areFriends(currentUserId, author.getId());
            case PRIVATE -> false;
        };
    }

    private ApiException postNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Post not found");
    }

}
