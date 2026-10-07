package com.sender.backend.user;

import com.sender.backend.auth.AuthDtos.PublicUser;
import com.sender.backend.storage.ObjectPublicUrl;
import com.sender.backend.storage.ObjectStorage;
import com.sender.backend.user.UserDtos.AvatarUploadRequest;
import com.sender.backend.user.UserDtos.AvatarUploadResponse;
import com.sender.backend.user.UserDtos.UpdateProfileRequest;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
	@Mock UserRepository users;
	@Mock ObjectStorage storage;

	private UserService service;

	@BeforeEach
	void setUp() {
		ObjectPublicUrl publicUrls = new ObjectPublicUrl("https://cdn.example.com");
		service = new UserService(users, storage, publicUrls, 300, 2 * 1024 * 1024);
	}

	@Test
	void updateDisplayNameTrimsAndPersists() throws Exception {
		User user = user(1, "alice");
		when(users.findById(1)).thenReturn(Optional.of(user));
		when(users.save(user)).thenReturn(user);

		PublicUser result = service.updateProfile(1, new UpdateProfileRequest("  Alice Nguyen  ", null));

		assertEquals("Alice Nguyen", result.displayName());
		assertEquals("Alice Nguyen", user.getDisplayName());
		verifyNoInteractions(storage);
	}

	@Test
	void updateDisplayNameRejectsBlank() throws Exception {
		User user = user(1, "alice");
		when(users.findById(1)).thenReturn(Optional.of(user));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.updateProfile(1, new UpdateProfileRequest("   ", null)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		verify(users, never()).save(any());
	}

	@Test
	void updateDisplayNameRejectsControlCharacters() throws Exception {
		User user = user(1, "alice");
		when(users.findById(1)).thenReturn(Optional.of(user));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.updateProfile(1, new UpdateProfileRequest("Alice\nNguyen", null)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		verify(users, never()).save(any());
	}

	@Test
	void updateRequiresAtLeastOneField() throws Exception {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.updateProfile(1, new UpdateProfileRequest(null, null)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		verifyNoInteractions(users, storage);
	}

	@Test
	void presignRejectsUnsupportedContentType() {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.createAvatarUpload(1, new AvatarUploadRequest("image/gif", 100L)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		verifyNoInteractions(storage);
	}

	@Test
	void presignRejectsOversizedContent() {
		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.createAvatarUpload(1, new AvatarUploadRequest("image/png", 3L * 1024 * 1024)));

		assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatusCode());
		verifyNoInteractions(storage);
	}

	@Test
	void presignReturnsPutUrlForOwnedKey() {
		when(storage.presignPut(anyString(), eq("image/png"), eq(1200L), eq(java.time.Duration.ofMinutes(5))))
				.thenReturn(new ObjectStorage.PresignedUpload(
						URI.create("https://s3.example/put"), Instant.parse("2026-10-06T12:00:00Z")));

		AvatarUploadResponse response = service.createAvatarUpload(1, new AvatarUploadRequest("image/png", 1200L));

		assertEquals("https://s3.example/put", response.putUrl());
		assertTrue(response.objectKey().startsWith("avatars/1/"));
		assertTrue(response.objectKey().endsWith(".png"));
		assertEquals("https://cdn.example.com/" + response.objectKey(), response.publicUrl());
		verify(storage).presignPut(eq(response.objectKey()), eq("image/png"), eq(1200L), eq(java.time.Duration.ofMinutes(5)));
	}

	@Test
	void commitRejectsObjectKeyForAnotherUser() throws Exception {
		User user = user(1, "alice");
		when(users.findById(1)).thenReturn(Optional.of(user));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.updateProfile(1, new UpdateProfileRequest(null, "avatars/2/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png")));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		verify(storage, never()).head(any());
		verify(users, never()).save(any());
	}

	@Test
	void commitPersistsPublicUrlWhenObjectExists() throws Exception {
		User user = user(1, "alice");
		String key = "avatars/1/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png";
		when(users.findById(1)).thenReturn(Optional.of(user));
		when(users.save(user)).thenReturn(user);
		when(storage.head(key)).thenReturn(Optional.of(new ObjectStorage.StoredObject(key, "image/png", 1200L)));

		PublicUser result = service.updateProfile(1, new UpdateProfileRequest(null, key));

		assertEquals("https://cdn.example.com/" + key, result.avatarUrl());
		assertEquals("https://cdn.example.com/" + key, user.getAvatarUrl());
		verify(storage, never()).delete(any());
	}

	@Test
	void commitDeletesPreviousObjectAfterSave() throws Exception {
		User user = user(1, "alice");
		user.setAvatarUrl("https://cdn.example.com/avatars/1/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg");
		String key = "avatars/1/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png";
		when(users.findById(1)).thenReturn(Optional.of(user));
		when(users.save(user)).thenReturn(user);
		when(storage.head(key)).thenReturn(Optional.of(new ObjectStorage.StoredObject(key, "image/png", 1200L)));

		service.updateProfile(1, new UpdateProfileRequest(null, key));

		ArgumentCaptor<String> deleted = ArgumentCaptor.forClass(String.class);
		verify(storage).delete(deleted.capture());
		assertEquals("avatars/1/bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg", deleted.getValue());
	}

	@Test
	void commitRejectsMissingObject() throws Exception {
		User user = user(1, "alice");
		String key = "avatars/1/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.png";
		when(users.findById(1)).thenReturn(Optional.of(user));
		when(storage.head(key)).thenReturn(Optional.empty());

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> service.updateProfile(1, new UpdateProfileRequest(null, key)));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		verify(users, never()).save(any());
	}

	private static User user(int id, String username) throws Exception {
		User user = new User(username, username + "@example.com", "hash");
		Field field = User.class.getDeclaredField("id");
		field.setAccessible(true);
		field.set(user, id);
		return user;
	}
}
