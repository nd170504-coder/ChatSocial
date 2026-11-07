package com.chatsocial.repository;

import com.chatsocial.entity.FriendRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import com.chatsocial.enums.FriendshipStatus;
import com.chatsocial.enums.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

public interface FriendRequestRepository extends JpaRepository<FriendRequest, String> {
    @Query("""
            select r from FriendRequest r join fetch r.requester sender join fetch r.addressee recipient
            where r.addressee.id = :userId and r.status = :status
              and sender.status = :active and recipient.status = :active
            """)
    Slice<FriendRequest> findIncoming(@Param("userId") String userId, @Param("status") FriendshipStatus status,
                                     @Param("active") UserStatus active, Pageable pageable);

    @Query("""
            select r from FriendRequest r join fetch r.requester sender join fetch r.addressee recipient
            where r.requester.id = :userId and r.status = :status
              and sender.status = :active and recipient.status = :active
            """)
    Slice<FriendRequest> findOutgoing(@Param("userId") String userId, @Param("status") FriendshipStatus status,
                                     @Param("active") UserStatus active, Pageable pageable);

    Optional<FriendRequest> findByPairKey(String pairKey);

    @Query("select r.pairKey from FriendRequest r where r.id = :id")
    Optional<String> findPairKeyById(@Param("id") String id);
}
