package com.chatsocial.service;

import com.chatsocial.config.SupabaseProperties;
import com.chatsocial.enums.ProfileImageType;
import com.chatsocial.exception.ApiException;
import com.chatsocial.utils.ProfileImageValidator.ValidatedImage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
public class SupabaseStorageService {
    private final SupabaseProperties properties;
    private final HttpClient client;
    private final ObjectMapper json;

    public SupabaseStorageService(SupabaseProperties properties, HttpClient client, ObjectMapper json) {
        this.properties = properties;
        this.client = client;
        this.json = json;
    }

    public record StoredImage(String path, String url) {}

    public record DownloadedImage(byte[] bytes, String contentType) {}

    public String newPostPath(String userId, ValidatedImage image) {
        return UUID.fromString(userId) + "/posts/" + UUID.randomUUID() + "." + image.extension();
    }

    public void uploadPostImage(String path, ValidatedImage image) {
        validatePostPath(path);
        HttpRequest upload = request(baseUrl() + "/storage/v1/object/" + postBucket() + "/" + path)
                .header("Content-Type", image.contentType()).header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(image.bytes())).build();
        send(upload);
    }

    public void deletePostImage(String path) {
        validatePostPath(path);
        HttpRequest deletion = request(baseUrl() + "/storage/v1/object/" + postBucket())
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(Map.of("prefixes", List.of(path)))))
                .build();
        send(deletion);
    }

    public DownloadedImage downloadPostImage(String path) {
        validatePostPath(path);
        HttpRequest download = request(baseUrl() + "/storage/v1/object/authenticated/"
                + postBucket() + "/" + path).GET().build();
        try {
            var response = client.send(download, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to download post image");
                }
                byte[] bytes = body.readNBytes(5 * 1024 * 1024 + 1);
                if (bytes.length > 5 * 1024 * 1024) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "Stored post image exceeds size limit");
                }
                String contentType = path.endsWith(".png") ? "image/png"
                        : path.endsWith(".webp") ? "image/webp" : "image/jpeg";
                return new DownloadedImage(bytes, contentType);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Storage request was interrupted");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to reach Supabase Storage");
        }
    }

    private String postBucket() {
        String bucket = properties.getPostBucket();
        if (bucket == null || !bucket.matches("[a-zA-Z0-9_-]+")) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Post storage bucket is not configured");
        }
        return bucket;
    }

    private void validatePostPath(String path) {
        if (path == null || !path.matches("[0-9a-f-]{36}/posts/[0-9a-f-]{36}\\.(jpg|png|webp)")) {
            throw new IllegalArgumentException("Invalid post image path");
        }
    }

    public StoredImage upload(String userId, ProfileImageType type, ValidatedImage image) {
        String baseUrl = baseUrl();
        String path = UUID.fromString(userId) + "/" + type.getFolder() + "/" + UUID.randomUUID()
                + "." + image.extension();
        HttpRequest request = request(baseUrl + "/storage/v1/object/" + properties.getBucket() + "/" + path)
                .header("Content-Type", image.contentType()).header("x-upsert", "false")
                .POST(HttpRequest.BodyPublishers.ofByteArray(image.bytes())).build();
        send(request);
        return new StoredImage(path, publicPrefix() + path);
    }

    public void delete(String path) {
        // Only backend-generated paths are accepted; never delete arbitrary URLs or bucket contents.
        if (path == null || !path.matches("[0-9a-f-]{36}/(avatars|covers)/[0-9a-f-]{36}\\.(jpg|png|webp)")) {
            throw new IllegalArgumentException("Invalid managed image path");
        }
        HttpRequest request = request(baseUrl() + "/storage/v1/object/" + properties.getBucket())
                .header("Content-Type", "application/json")
                .method("DELETE", HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(Map.of("prefixes", List.of(path)))))
                .build();
        send(request);
    }

    public Optional<String> managedPath(String url, String userId, ProfileImageType type) {
        if (url == null) return Optional.empty();
        String prefix = publicPrefix() + userId + "/" + type.getFolder() + "/";
        if (!url.startsWith(prefix)) return Optional.empty();
        String filename = url.substring(prefix.length());
        if (!filename.matches("[0-9a-f-]{36}\\.(jpg|png|webp)")) return Optional.empty();
        return Optional.of(userId + "/" + type.getFolder() + "/" + filename);
    }

    private String publicPrefix() {
        return baseUrl() + "/storage/v1/object/public/" + properties.getBucket() + "/";
    }

    private String baseUrl() {
        if (properties.getUrl() == null || properties.getUrl().isBlank()
                || properties.getSecretKey() == null || properties.getSecretKey().isBlank()
                || properties.getBucket() == null || !properties.getBucket().matches("[a-zA-Z0-9_-]+")) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Supabase Storage is not configured");
        }
        URI uri;
        try {
            uri = URI.create(properties.getUrl());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Supabase Storage URL is invalid");
        }
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Supabase Storage URL is invalid");
        }
        return properties.getUrl().replaceAll("/+$", "");
    }

    private HttpRequest.Builder request(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("apikey", properties.getSecretKey());
        if (!properties.getSecretKey().startsWith("sb_secret_")) {
            builder.header("Authorization", "Bearer " + properties.getSecretKey());
        }
        return builder;
    }

    private void send(HttpRequest request) {
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Supabase Storage request failed");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Storage request was interrupted");
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Unable to reach Supabase Storage");
        }
    }
}
