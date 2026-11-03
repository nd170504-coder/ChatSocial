package com.chatsocial.service;

import com.chatsocial.dto.user.AccountActionRequest;
import com.chatsocial.entity.User;
import com.chatsocial.entity.UserSession;
import com.chatsocial.enums.UserStatus;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionService userSessionService;

    @Transactional
    public void deactivate(String userId, AccountActionRequest accountActionRequest) {
        changeStatus(userId, accountActionRequest, UserStatus.DISABLED);
    }

    @Transactional
    public void deleteAccount(String userId, AccountActionRequest accountActionRequest) {
        changeStatus(userId, accountActionRequest, UserStatus.DELETED);
    }

    private void changeStatus(String userId, AccountActionRequest accountActionRequest, UserStatus userStatus) {
        User user = userRepository.findForUpdateById(userId)
                .filter(value -> value.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "User is unavailable"));

        String currentPassword = accountActionRequest.getCurrentPassword();

        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > 72 || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        user.setStatus(userStatus);
        userSessionService.revokeAll(userId);
    }

}
