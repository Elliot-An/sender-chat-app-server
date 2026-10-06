package com.sender.backend.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(Integer conversationId, Integer senderId, UUID clientMessageId);
    Optional<Message> findTopByConversationIdOrderByCreatedAtDescIdDesc(Integer conversationId);

    @Query("""
        select m from Message m
        where m.conversation.id = :conversationId
          and (m.createdAt < :createdAt or (m.createdAt = :createdAt and m.id < :id))
        order by m.createdAt desc, m.id desc
        """)
    List<Message> findBefore(@Param("conversationId") Integer conversationId,
                             @Param("createdAt") Instant createdAt,
                             @Param("id") Long id,
                             Pageable pageable);

    List<Message> findByConversationIdOrderByCreatedAtDescIdDesc(Integer conversationId, Pageable pageable);

    @Query("""
        select distinct m.sender.id from Message m
        where m.conversation.id = :conversationId and m.id <= :messageId
        """)
    List<Integer> findSenderIdsThrough(@Param("conversationId") Integer conversationId,
                                       @Param("messageId") Long messageId);

    @Query("""
        select count(m) from Message m
        where m.conversation.id = :conversationId
          and m.sender.id <> :userId
        """)
    long countUnread(@Param("conversationId") Integer conversationId,
                     @Param("userId") Integer userId);

    @Query("""
        select count(m) from Message m
        where m.conversation.id = :conversationId
          and m.sender.id <> :userId
          and (
            m.createdAt > :readAt
            or (m.createdAt = :readAt and m.id > :readId)
          )
        """)
    long countUnreadAfter(@Param("conversationId") Integer conversationId,
                          @Param("userId") Integer userId,
                          @Param("readAt") Instant readAt,
                          @Param("readId") Long readId);
}
