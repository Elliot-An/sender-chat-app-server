package com.sender.backend.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObjectPublicUrlTest {
	@Test
	void buildsAndParsesKeysAgainstTrimmedBase() {
		ObjectPublicUrl urls = new ObjectPublicUrl("https://cdn.example.com/");

		assertEquals("https://cdn.example.com/avatars/1/a.png", urls.of("avatars/1/a.png"));
		assertEquals("avatars/1/a.png", urls.objectKeyFrom("https://cdn.example.com/avatars/1/a.png"));
		assertNull(urls.objectKeyFrom("https://other.example.com/avatars/1/a.png"));
		assertNull(urls.objectKeyFrom(null));
	}
}
