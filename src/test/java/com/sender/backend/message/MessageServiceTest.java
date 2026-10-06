package com.sender.backend.message;

import com.sender.backend.conversation.*;
import com.sender.backend.user.User;
import com.sender.backend.message.MessageDtos.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    @Mock MessageRepository messages;
    @Mock ConversationRepository conversations;
    @Mock ConversationMemberRepository members;
    @Mock com.sender.backend.user.UserRepository users;
    @Mock com.sender.backend.realtime.RealtimePublisher realtime;

    @Test
    void retryWithSameClientIdAndBodyReturnsExistingMessage() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        User sender = new User("alice", "alice@example.com", "hash");
        setId(sender, 1);
        UUID clientId = UUID.randomUUID();
        Message existing = new Message(conversation, sender, "hello", clientId);
        setId(existing, 42L);

        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(members.findByConversationId(9)).thenReturn(List.of());
        when(conversations.findByIdForUpdate(9)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(9, 1, clientId))
                .thenReturn(Optional.of(existing));

        MessageResponse response = new MessageService(messages, conversations, members, users, realtime)
                .send(1, 9, new SendRequest("hello", clientId));

        assertEquals(42L, response.id());
        verify(messages, never()).save(any());
    }

    @Test
    void retryWithDifferentBodyConflicts() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        User sender = new User("alice", "alice@example.com", "hash");
        setId(sender, 1);
        UUID clientId = UUID.randomUUID();
        Message existing = new Message(conversation, sender, "hello", clientId);

        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(conversations.findByIdForUpdate(9)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(9, 1, clientId))
                .thenReturn(Optional.of(existing));

        assertThrows(ResponseStatusException.class, () -> new MessageService(messages, conversations, members, users, realtime)
                .send(1, 9, new SendRequest("different", clientId)));
        verify(messages, never()).save(any());
    }

    @Test
    void searchRejectsQueriesOutsideTheSupportedLength() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(conversations.findById(9)).thenReturn(Optional.of(conversation));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new MessageService(messages, conversations, members, users, realtime)
                        .search(1, 9, "a", null, 30));

        assertEquals(400, exception.getStatusCode().value());
        verify(messages, never()).search(anyInt(), anyString(), any());
    }

    @Test
    void searchRejectsNonMembersBeforeQueryingMessages() throws Exception {
        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(false);
        when(conversations.existsById(9)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new MessageService(messages, conversations, members, users, realtime)
                        .search(1, 9, "hello", null, 30));

        assertEquals(403, exception.getStatusCode().value());
        verify(messages, never()).search(anyInt(), anyString(), any());
    }

    private static void setId(Object target, Object value) throws Exception {
        Field field = target instanceof Message ? Message.class.getDeclaredField("id")
                : target instanceof Conversation ? Conversation.class.getDeclaredField("id")
                : User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, value);
    }
}
