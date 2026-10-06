package com.sender.backend.conversation;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

public interface ConversationRepository extends JpaRepository<Conversation, Integer> {
    Optional<Conversation> findByDirectKey(String directKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") Integer id);

    @Modifying
    @Query(value = """
        insert into conversations (type, direct_key, created_at, updated_at)
        values ('DIRECT', :directKey, :createdAt, :updatedAt)
        on conflict (direct_key) do nothing
        """, nativeQuery = true)
    int insertDirectIfAbsent(@Param("directKey") String directKey,
                             @Param("createdAt") Instant createdAt,
                             @Param("updatedAt") Instant updatedAt);

    @Query("""
        select c from Conversation c
        join ConversationMember m on m.conversation = c
        where m.user.id = :userId
        order by c.updatedAt desc, c.id desc
        """)
    List<Conversation> findForUser(@Param("userId") Integer userId, Pageable pageable);

    @Query("""
        select c from Conversation c
        join ConversationMember m on m.conversation = c
        where m.user.id = :userId
          and (c.updatedAt < :updatedAt or (c.updatedAt = :updatedAt and c.id < :id))
        order by c.updatedAt desc, c.id desc
        """)
    List<Conversation> findForUserBefore(@Param("userId") Integer userId,
                                          @Param("updatedAt") Instant updatedAt,
                                          @Param("id") Integer id,
                                          Pageable pageable);
}
