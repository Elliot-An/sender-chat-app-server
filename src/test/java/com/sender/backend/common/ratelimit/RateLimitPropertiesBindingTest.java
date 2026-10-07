package com.sender.backend.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the shipped application.properties wires every policy to its .env variable. */
class RateLimitPropertiesBindingTest {
	private static final String MAIN_PROPERTIES = "src/main/resources/application.properties";

	@Test
	void bindsDefaultsForEveryPolicy() throws Exception {
		RateLimitProperties properties = bind(Map.of());

		assertEquals(Duration.ofMinutes(1), properties.window());
		assertEquals(RateLimitPolicy.values().length, properties.maxHits().size());
	}

	@Test
	void environmentVariablesOverrideMaxHits() throws Exception {
		RateLimitProperties properties = bind(Map.of(
				"RATE_LIMIT_MESSAGE_SEND_MAX_HITS", "7",
				"RATE_LIMIT_WINDOW", "30s"));

		assertEquals(7, properties.maxHits().get(RateLimitPolicy.MESSAGE_SEND));
		assertEquals(Duration.ofSeconds(30), properties.window());
	}

	private RateLimitProperties bind(Map<String, Object> environment) throws Exception {
		List<PropertySource<?>> loaded = new PropertiesPropertySourceLoader()
				.load("application", new FileSystemResource(MAIN_PROPERTIES));
		MutablePropertySources sources = new MutablePropertySources();
		sources.addFirst(new MapPropertySource("env", environment));
		loaded.forEach(sources::addLast);
		return new Binder(ConfigurationPropertySources.from(sources),
				new PropertySourcesPlaceholdersResolver(sources))
				.bind("app.rate-limit", RateLimitProperties.class).get();
	}
}
