package com.chatsocial.utils;

import com.chatsocial.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ProfileImageValidator {
    public static final long MAX_BYTES = 5 * 1024 * 1024;

    private ProfileImageValidator() {}

    public record ValidatedImage(byte[] bytes, String contentType, String extension) {}

    public static ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file must not be empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Image must not exceed 5 MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unable to read image file");
        }
        String contentType;
        String extension;
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
            contentType = "image/png";
            extension = "png";
        } else if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) {
            contentType = "image/jpeg";
            extension = "jpg";
        } else if (bytes.length >= 12 && new String(bytes, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            contentType = "image/webp";
            extension = "webp";
        } else {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only JPEG, PNG and WebP images are supported");
        }
        if (!contentType.equalsIgnoreCase(file.getContentType())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Image content does not match its Content-Type");
        }
        return new ValidatedImage(bytes, contentType, extension);
    }
}
