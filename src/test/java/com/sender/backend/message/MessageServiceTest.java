package com.sender.backend.message;

import com.sender.backend.conversation.*;
import com.sender.backend.message.MessageDtos.*;
import com.sender.backend.storage.ObjectStorage;
import com.sender.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {
    @Mock MessageRepository messages;
    @Mock ConversationRepository conversations;
    @Mock ConversationMemberRepository members;
    @Mock com.sender.backend.user.UserRepository users;
    @Mock com.sender.backend.realtime.RealtimePublisher realtime;
    @Mock ObjectStorage storage;

    MessageService service;

    @BeforeEach
    void setUp() {
        service = new MessageService(messages, conversations, members, users, realtime, storage, 300, 10 * 1024 * 1024);
    }

    @Test
    void retryWithSameClientIdAndBodyReturnsExistingMessage() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        User sender = new User("alice", "alice@example.com", "hash");
        setId(sender, 1);
        UUID clientId = UUID.randomUUID();
        Message existing = new Message(conversation, sender, "hello", clientId, List.of());
        setId(existing, 42L);

        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(members.findByConversationId(9)).thenReturn(List.of());
        when(conversations.findByIdForUpdate(9)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(9, 1, clientId))
                .thenReturn(Optional.of(existing));

        MessageResponse response = service.send(1, 9, new SendRequest("hello", clientId, List.of()));

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
        Message existing = new Message(conversation, sender, "hello", clientId, List.of());

        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(conversations.findByIdForUpdate(9)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(9, 1, clientId))
                .thenReturn(Optional.of(existing));

        assertThrows(ResponseStatusException.class, () ->
                service.send(1, 9, new SendRequest("different", clientId, List.of())));
        verify(messages, never()).save(any());
    }

    @Test
    void sendRejectsEmptyBodyWithoutAttachments() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(conversations.findByIdForUpdate(9)).thenReturn(Optional.of(conversation));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.send(1, 9, new SendRequest("  ", UUID.randomUUID(), List.of())));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(messages, never()).save(any());
    }

    @Test
    void sendAttachmentOnlyMessagePersistsMetadata() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        User sender = new User("alice", "alice@example.com", "hash");
        setId(sender, 1);
        UUID clientId = UUID.randomUUID();
        String key = "message-attachments/9/1/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.pdf";

        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(conversations.findByIdForUpdate(9)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(9, 1, clientId))
                .thenReturn(Optional.empty());
        when(users.findById(1)).thenReturn(Optional.of(sender));
        when(storage.head(key)).thenReturn(Optional.of(new ObjectStorage.StoredObject(key, "application/pdf", 1200L)));
        when(members.findByConversationId(9)).thenReturn(List.of());
        when(messages.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            setId(message, 55L);
            return message;
        });

        MessageResponse response = service.send(1, 9, new SendRequest(null, clientId,
                List.of(new AttachmentCommitRequest(key, "notes.pdf"))));

        assertNull(response.body());
        assertEquals(1, response.attachments().size());
        assertEquals("notes.pdf", response.attachments().getFirst().originalFilename());
        assertEquals("application/pdf", response.attachments().getFirst().contentType());
        assertEquals(1200L, response.attachments().getFirst().sizeBytes());
        verify(messages).save(any(Message.class));
    }

    @Test
    void sendRejectsMissingAttachmentObject() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        UUID clientId = UUID.randomUUID();
        String key = "message-attachments/9/1/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.pdf";

        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(conversations.findByIdForUpdate(9)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(9, 1, clientId))
                .thenReturn(Optional.empty());
        when(storage.head(key)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.send(1, 9, new SendRequest(null, clientId,
                        List.of(new AttachmentCommitRequest(key, "notes.pdf")))));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(messages, never()).save(any());
    }

    @Test
    void retryWithSameAttachmentsIsIdempotent() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        User sender = new User("alice", "alice@example.com", "hash");
        setId(sender, 1);
        UUID clientId = UUID.randomUUID();
        String key = "message-attachments/9/1/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.pdf";
        MessageAttachment stored = new MessageAttachment(UUID.randomUUID(), key, "notes.pdf",
                "application/pdf", 1200L, 0);
        Message existing = new Message(conversation, sender, null, clientId, List.of(stored));
        setId(existing, 42L);

        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(members.findByConversationId(9)).thenReturn(List.of());
        when(conversations.findByIdForUpdate(9)).thenReturn(Optional.of(conversation));
        when(messages.findByConversationIdAndSenderIdAndClientMessageId(9, 1, clientId))
                .thenReturn(Optional.of(existing));

        MessageResponse response = service.send(1, 9, new SendRequest(null, clientId,
                List.of(new AttachmentCommitRequest(key, "notes.pdf"))));

        assertEquals(42L, response.id());
        verify(messages, never()).save(any());
        verify(storage, never()).head(anyString());
    }

    @Test
    void createAttachmentUploadRequiresMembership() {
        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(false);
        when(conversations.existsById(9)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createAttachmentUpload(1, 9,
                        new AttachmentUploadRequest("application/pdf", 100L, "notes.pdf")));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(storage, never()).presignPut(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void createAttachmentUploadRejectsDisallowedType() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(conversations.findById(9)).thenReturn(Optional.of(conversation));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createAttachmentUpload(1, 9,
                        new AttachmentUploadRequest("image/gif", 100L, "x.gif")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void downloadRequiresMembership() throws Exception {
        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(false);
        when(conversations.existsById(9)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createDownload(1, 9, 42L, UUID.randomUUID()));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(storage, never()).presignGet(anyString(), any());
    }

    @Test
    void downloadReturnsPresignedGetForMember() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        User sender = new User("alice", "alice@example.com", "hash");
        setId(sender, 1);
        UUID attachmentId = UUID.randomUUID();
        String key = "message-attachments/9/1/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.pdf";
        Message message = new Message(conversation, sender, null, UUID.randomUUID(),
                List.of(new MessageAttachment(attachmentId, key, "notes.pdf", "application/pdf", 100L, 0)));
        setId(message, 42L);

        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(conversations.findById(9)).thenReturn(Optional.of(conversation));
        when(messages.findById(42L)).thenReturn(Optional.of(message));
        when(storage.presignGet(eq(key), any())).thenReturn(
                new ObjectStorage.PresignedDownload(URI.create("https://s3.example/get"), Instant.parse("2026-01-01T00:05:00Z")));

        AttachmentDownloadResponse response = service.createDownload(1, 9, 42L, attachmentId);

        assertEquals("https://s3.example/get", response.getUrl());
    }

    @Test
    void searchRejectsQueriesOutsideTheSupportedLength() throws Exception {
        Conversation conversation = Conversation.direct("1:2");
        setId(conversation, 9);
        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(true);
        when(conversations.findById(9)).thenReturn(Optional.of(conversation));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.search(1, 9, "a", null, 30));

        assertEquals(400, exception.getStatusCode().value());
        verify(messages, never()).search(anyInt(), anyString(), any());
    }

    @Test
    void searchRejectsNonMembersBeforeQueryingMessages() {
        when(members.existsByConversationIdAndUserId(9, 1)).thenReturn(false);
        when(conversations.existsById(9)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.search(1, 9, "hello", null, 30));

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
