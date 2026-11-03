package com.chatsocial.service;

import com.chatsocial.dto.profile.*;
import com.chatsocial.entity.UserProfile;
import com.chatsocial.enums.ProfileImageType;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.UserProfileRepository;
import com.chatsocial.utils.ProfileImageValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class ProfileService {
    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private final UserProfileRepository profiles;
    private final SupabaseStorageService storage;
    private final TransactionTemplate transactions;

    public ProfileService(UserProfileRepository profiles, SupabaseStorageService storage,
                          PlatformTransactionManager transactionManager) {
        this.profiles = profiles;
        this.storage = storage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(String userId) {
        return ProfileResponse.from(profiles.findByUserId(userId).orElseThrow(this::profileNotFound));
    }

    @Transactional
    public ProfileResponse update(String userId, UpdateProfileRequest request) {
        UserProfile profile = profiles.findForUpdateByUserId(userId).orElseThrow(this::profileNotFound);
        profile.setFullName(request.getFullName());
        profile.setBio(request.getBio());

        LocalDate dateOfBirth = request.getDateOfBirth();
        if (dateOfBirth != null && dateOfBirth.isAfter(LocalDate.now().minusYears(15))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bạn phải đủ 15 tuổi trở lên");
        }
        profile.setDateOfBirth(dateOfBirth);
        profile.setGender(request.getGender());
        profiles.flush();
        return ProfileResponse.from(profile);
    }

    public ProfileResponse uploadImage(String userId, ProfileImageType type, MultipartFile file) {
        var image = ProfileImageValidator.validate(file);
        if (profiles.findByUserId(userId).isEmpty()) throw profileNotFound();
        // Do network I/O outside the database transaction and row lock.
        var uploaded = storage.upload(userId, type, image);
        ImageUpdate result;
        try {
            result = Objects.requireNonNull(transactions.execute(status -> {
                UserProfile profile = profiles.findForUpdateByUserId(userId).orElseThrow(this::profileNotFound);
                String previousUrl = type == ProfileImageType.AVATAR ? profile.getAvatarUrl() : profile.getCoverUrl();
                if (type == ProfileImageType.AVATAR) profile.setAvatarUrl(uploaded.url());
                else profile.setCoverUrl(uploaded.url());
                profiles.flush();
                return new ImageUpdate(ProfileResponse.from(profile), previousUrl);
            }));
        } catch (RuntimeException exception) {
            deleteQuietly(uploaded.path());
            throw exception;
        }
        // Only remove the old object after the new URL has committed successfully.
        storage.managedPath(result.previousUrl(), userId, type).ifPresent(this::deleteQuietly);
        return result.profile();
    }

    private void deleteQuietly(String path) {
        try {
            storage.delete(path);
        } catch (RuntimeException exception) {
            log.warn("Could not clean up stored profile image at {}; manual cleanup may be needed", path);
        }
    }

    private ApiException profileNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "User profile not found");
    }

    private record ImageUpdate(ProfileResponse profile, String previousUrl) {}
}
