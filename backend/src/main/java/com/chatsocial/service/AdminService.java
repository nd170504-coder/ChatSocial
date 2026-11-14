package com.chatsocial.service;

import com.chatsocial.dto.user.AdminUserResponse;
import com.chatsocial.enums.*;
import com.chatsocial.exception.ApiException;
import com.chatsocial.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository users;
    private final UserSessionService sessions;

    public record UserPage(long totalUsers, long totalMatches, int page, boolean hasNext,
                           List<AdminUserResponse> items) {}

    public record DailyRegistrations(LocalDate date, long count) {}
    public record StatusTotal(UserStatus status, long count) {}
    public record Overview(long totalUsers, long newUsersToday, long newUsersInPeriod,
                           String timezone, List<DailyRegistrations> registrations,
                           List<StatusTotal> statuses) {}

    @Transactional(readOnly = true)
    public Overview overview(int days) {
        if (days != 7 && days != 30) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ thống kê 7 hoặc 30 ngày.");
        }
        var timezone = ZoneId.of("Asia/Ho_Chi_Minh");
        var now = Instant.now();
        var today = now.atZone(timezone).toLocalDate();
        var firstDay = today.minusDays(days - 1L);
        var daily = new LinkedHashMap<LocalDate, Long>();
        for (int day = 0; day < days; day++) daily.put(firstDay.plusDays(day), 0L);
        var registrations = users.findRegistrationTimes(firstDay.atStartOfDay(timezone).toInstant(), now);
        for (var createdAt : registrations) {
            var date = createdAt.atZone(timezone).toLocalDate();
            daily.computeIfPresent(date, (key, count) -> count + 1);
        }
        var counts = new EnumMap<UserStatus, Long>(UserStatus.class);
        for (var row : users.countByStatus()) counts.put(row.getStatus(), row.getTotal());
        var statuses = new ArrayList<StatusTotal>();
        for (var status : UserStatus.values()) statuses.add(new StatusTotal(status, counts.getOrDefault(status, 0L)));
        return new Overview(counts.values().stream().mapToLong(Long::longValue).sum(), daily.get(today),
                registrations.size(), timezone.getId(),
                daily.entrySet().stream().map(entry -> new DailyRegistrations(entry.getKey(), entry.getValue())).toList(),
                statuses);
    }

    @Transactional(readOnly = true)
    public UserPage list(String query, int page, int size) {
        if (page < 0 || size < 1 || size > 50 || query.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tham số tìm kiếm không hợp lệ.");
        }
        String escaped = query.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var result = users.searchForAdmin(UserRole.USER, "%" + escaped + "%", PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new UserPage(users.countByRole(UserRole.USER), result.getTotalElements(), page, result.hasNext(),
                result.map(AdminUserResponse::from).getContent());
    }

    @Transactional
    public AdminUserResponse changeStatus(String adminId, String userId, boolean suspended, String reason) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isBlank() || normalized.length() > 500) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ 1 đến 500 ký tự.");
        }
        var user = users.findForUpdateById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản."));
        if (userId.equals(adminId) || user.getRole() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể khóa hoặc mở khóa tài khoản quản trị.");
        }
        UserStatus expected = suspended ? UserStatus.ACTIVE : UserStatus.SUSPENDED;
        if (user.getStatus() != expected) {
            throw new ApiException(HttpStatus.CONFLICT, "Trạng thái tài khoản đã thay đổi hoặc không hỗ trợ thao tác này.");
        }
        user.setStatus(suspended ? UserStatus.SUSPENDED : UserStatus.ACTIVE);
        user.setModerationReason(normalized);
        user.setModeratedBy(adminId);
        user.setModeratedAt(Instant.now());
        // Old sessions stay revoked after an unlock; the user must log in again.
        sessions.revokeAll(userId);
        return AdminUserResponse.from(user);
    }
}
