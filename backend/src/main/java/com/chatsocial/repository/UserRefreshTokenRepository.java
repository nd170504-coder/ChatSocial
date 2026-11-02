package com.chatsocial.repository;

import com.chatsocial.entity.UserRefreshToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UserRefreshTokenRepository extends JpaRepository<UserRefreshToken, String> {
    @Query("select t.session.id from UserRefreshToken t where t.tokenHash = :hash")
    Optional<String> findSessionIdByTokenHash(@Param("hash") String hash);

    Optional<UserRefreshToken> findByTokenHash(String tokenHash);
}
