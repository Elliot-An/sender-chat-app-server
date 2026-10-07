package com.sender.backend.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface MessageRepository extends JpaRepository<Message, Long> {
    interface SearchRow {
        Long getId();
        Instant getCreatedAt();
        Double getRank();
        String getSnippet();
    }

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

    List<Message> findByConversationId(Integer conversationId);

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

    @Query(value = """
        select m.id as id, m.created_at as createdAt,
               ts_rank_cd(to_tsvector('simple', immutable_unaccent(m.body)),
                          plainto_tsquery('simple', immutable_unaccent(:query))) as rank,
               substring(m.body from 1 for 240) as snippet
        from messages m
        where m.conversation_id = :conversationId
          and to_tsvector('simple', immutable_unaccent(m.body))
              @@ plainto_tsquery('simple', immutable_unaccent(:query))
        order by rank desc, m.created_at desc, m.id desc
        """, nativeQuery = true)
    List<SearchRow> search(@Param("conversationId") Integer conversationId,
                           @Param("query") String query,
                           Pageable pageable);

    @Query(value = """
        select m.id as id, m.created_at as createdAt,
               ts_rank_cd(to_tsvector('simple', immutable_unaccent(m.body)),
                          plainto_tsquery('simple', immutable_unaccent(:query))) as rank,
               substring(m.body from 1 for 240) as snippet
        from messages m
        where m.conversation_id = :conversationId
          and to_tsvector('simple', immutable_unaccent(m.body))
              @@ plainto_tsquery('simple', immutable_unaccent(:query))
          and (
            ts_rank_cd(to_tsvector('simple', immutable_unaccent(m.body)),
                       plainto_tsquery('simple', immutable_unaccent(:query))) < :rank
            or (
              ts_rank_cd(to_tsvector('simple', immutable_unaccent(m.body)),
                         plainto_tsquery('simple', immutable_unaccent(:query))) = :rank
              and (m.created_at < :createdAt
                   or (m.created_at = :createdAt and m.id < :id))
            )
          )
        order by rank desc, m.created_at desc, m.id desc
        """, nativeQuery = true)
    List<SearchRow> searchBefore(@Param("conversationId") Integer conversationId,
                                 @Param("query") String query,
                                 @Param("rank") double rank,
                                 @Param("createdAt") Instant createdAt,
                                 @Param("id") Long id,
                                 Pageable pageable);
}
