package com.sender.backend.conversation;

import com.sender.backend.user.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ConversationMemberProgressTest {
    @Test
    void readAdvancesDeliveryAndOlderMessagesDoNotRegressCursors() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 1);
        User user = new User("alice", "alice@example.com", "hash");
        setId(user, 1);
        ConversationMember member = new ConversationMember(conversation, user);
        Instant first = member.getJoinedAt().plusSeconds(1);
        Instant second = first.plusSeconds(1);

        member.advanceRead(20L, second);
        member.advanceDelivered(10L, first);

        assertEquals(20L, member.getLastDeliveredMessageId());
        assertEquals(second, member.getLastDeliveredAt());
        assertEquals(20L, member.getLastReadMessageId());
        assertEquals(second, member.getLastReadAt());
    }

    @Test
    void sameTimestampUsesMessageIdAsTieBreaker() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 1);
        User user = new User("alice", "alice@example.com", "hash");
        setId(user, 1);
        ConversationMember member = new ConversationMember(conversation, user);
        Instant createdAt = member.getJoinedAt().plusSeconds(1);

        member.advanceDelivered(20L, createdAt);
        member.advanceDelivered(10L, createdAt);

        assertEquals(20L, member.getLastDeliveredMessageId());
    }

    private static void setId(Object target, Object value) throws Exception {
        Field field = target instanceof Conversation
                ? Conversation.class.getDeclaredField("id")
                : User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, value);
    }
}
