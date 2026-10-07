package com.sender.backend.conversation;

import com.sender.backend.conversation.ConversationDtos.*;
import com.sender.backend.friendship.FriendshipRepository;
import com.sender.backend.message.MessageProgressService;
import com.sender.backend.message.MessageRepository;
import com.sender.backend.presence.PresenceService;
import com.sender.backend.realtime.RealtimePublisher;
import com.sender.backend.storage.ObjectPublicUrl;
import com.sender.backend.storage.ObjectStorage;
import com.sender.backend.user.User;
import com.sender.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {
	@Mock ConversationRepository conversations;
	@Mock ConversationMemberRepository members;
	@Mock MessageRepository messages;
	@Mock FriendshipRepository friendships;
	@Mock UserRepository users;
	@Mock MessageProgressService progress;
	@Mock PresenceService presence;
	@Mock RealtimePublisher realtime;
	@Mock ObjectStorage storage;

	private ConversationService service;

	@BeforeEach
	void setUp() {
		service = new ConversationService(
				conversations, members, messages, friendships, users, progress, presence,
				realtime, storage, new ObjectPublicUrl("https://cdn.example.com"),
				300, 2 * 1024 * 1024);
	}

	@Test
	void memberCanRenameGroup() throws Exception {
		Conversation group = group(10, "Old");
		User alice = user(1, "alice");
		stubLockedMember(1, group);
		when(members.findByConversationId(10)).thenReturn(List.of(new ConversationMember(group, alice)));

		ConversationResponse result = service.updateGroup(1, 10, new UpdateGroupRequest("  New name  ", null));

		assertEquals("New name", result.name());
		assertEquals("New name", group.getName());
		verify(realtime).publishAfterCommit(eq(1), eq("CONVERSATION_UPDATED"), any());
	}

	@Test
	void renameRejectedForDirectConversation() throws Exception {
		Conversation direct = Conversation.direct("1:2");
		setId(direct, 5);
		stubLockedMember(1, direct);

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.updateGroup(1, 5, new UpdateGroupRequest("Nope", null)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
	}

	@Test
	void lastMemberLeaveDissolvesGroup() throws Exception {
		Conversation group = group(10, "Solo");
		User alice = user(1, "alice");
		ConversationMember membership = new ConversationMember(group, alice);
		stubLockedMember(1, group);
		when(members.existsByConversationIdAndUserId(10, 1)).thenReturn(true);
		when(members.countByConversationId(10)).thenReturn(1L);
		when(members.findByConversationId(10)).thenReturn(List.of(membership));

		Optional<ConversationResponse> result = service.removeMember(1, 10, 1);

		assertTrue(result.isEmpty());
		verify(members).deleteAll(List.of(membership));
		verify(conversations).delete(group);
		ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
		verify(realtime).publishAfterCommit(eq(1), eq("CONVERSATION_UPDATED"), payload.capture());
		ConversationUpdatedPayload updated = (ConversationUpdatedPayload) payload.getValue();
		assertEquals(10, updated.conversationId());
		assertEquals(ConversationUpdatedPayload.Membership.DISSOLVED, updated.membership());
		assertNull(updated.conversation());
	}

	@Test
	void nonLastLeaveKeepsGroupForOthers() throws Exception {
		Conversation group = group(10, "Crew");
		User alice = user(1, "alice");
		User bob = user(2, "bob");
		stubLockedMember(1, group);
		when(members.existsByConversationIdAndUserId(10, 1)).thenReturn(true);
		when(members.countByConversationId(10)).thenReturn(2L);
		when(members.findByConversationId(10)).thenReturn(
				List.of(new ConversationMember(group, bob)));

		Optional<ConversationResponse> result = service.removeMember(1, 10, 1);

		assertTrue(result.isEmpty());
		verify(members).deleteById(new ConversationMember.Id(10, 1));
		verify(conversations, never()).delete(any());
		verify(realtime).publishAfterCommit(eq(1), eq("CONVERSATION_UPDATED"), any());
		verify(realtime).publishAfterCommit(eq(2), eq("CONVERSATION_UPDATED"), any());
	}

	@Test
	void groupAvatarUploadRequiresMembership() {
		when(members.existsByConversationIdAndUserId(10, 1)).thenReturn(false);

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.createGroupAvatarUpload(1, 10, new ConversationDtos.AvatarUploadRequest("image/png", 100L)));

		assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
		verifyNoInteractions(storage);
	}

	@Test
	void groupAvatarUploadReturnsOwnedKey() throws Exception {
		Conversation group = group(10, "Crew");
		when(members.existsByConversationIdAndUserId(10, 1)).thenReturn(true);
		when(conversations.findById(10)).thenReturn(Optional.of(group));
		when(storage.presignPut(anyString(), eq("image/png"), eq(100L), any()))
				.thenReturn(new ObjectStorage.PresignedUpload(
						URI.create("https://s3.example/put"), Instant.parse("2026-10-07T00:00:00Z")));

		var response = service.createGroupAvatarUpload(1, 10, new ConversationDtos.AvatarUploadRequest("image/png", 100L));

		assertTrue(response.objectKey().startsWith("group-avatars/10/"));
		assertTrue(response.objectKey().endsWith(".png"));
	}

	private void stubLockedMember(Integer userId, Conversation conversation) {
		when(members.existsByConversationIdAndUserId(conversation.getId(), userId)).thenReturn(true);
		when(conversations.findByIdForUpdate(conversation.getId())).thenReturn(Optional.of(conversation));
	}

	private static Conversation group(int id, String name) throws Exception {
		Conversation conversation = Conversation.group(name);
		setId(conversation, id);
		return conversation;
	}

	private static User user(int id, String username) throws Exception {
		User user = new User(username, username + "@example.com", "hash");
		Field field = User.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(user, id);
		return user;
	}

	private static void setId(Conversation conversation, int id) throws Exception {
		Field field = Conversation.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(conversation, id);
	}
}
