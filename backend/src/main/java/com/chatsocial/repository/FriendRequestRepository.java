package com.chatsocial.repository;

import com.chatsocial.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, String> {
    Optional<FriendRequest> findByPairKey(String pairKey);
}
