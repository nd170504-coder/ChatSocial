package com.chatsocial.repository;

import com.chatsocial.entity.MessageMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageMediaRepository extends JpaRepository<MessageMedia, String> {

    List<MessageMedia> findByMessageIdOrderByIdAsc(String messageId);

    Optional<MessageMedia> findByIdAndMessageId(String id, String messageId);

    boolean existsByMessageId(String messageId);

}
