package com.sender.backend.common.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RateLimitInterceptorTest {
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		RateLimiter limiter = new RateLimiter(new RateLimitProperties(Duration.ofMinutes(1),
				Arrays.stream(RateLimitPolicy.values()).collect(Collectors.toMap(p -> p, p -> 2))));
		mvc = MockMvcBuilders.standaloneSetup(new SampleController())
				.addInterceptors(new RateLimitInterceptor(limiter))
				.setControllerAdvice(new RateLimitExceptionHandler())
				.build();
	}

	@Test
	void returns429WithRetryAfterOnceTheLimitIsExceeded() throws Exception {
		mvc.perform(get("/api/v1/sample/limited")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/sample/limited")).andExpect(status().isOk());

		mvc.perform(get("/api/v1/sample/limited"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists(HttpHeaders.RETRY_AFTER))
				.andExpect(jsonPath("$.code").value("RATE_LIMITED"));
	}

	@Test
	void doesNotLimitEndpointsWithoutTheAnnotation() throws Exception {
		for (int i = 0; i < 5; i++) {
			mvc.perform(get("/api/v1/sample/open")).andExpect(status().isOk());
		}
	}

	@RestController
	@RequestMapping("/api/v1/sample")
	static class SampleController {
		@GetMapping("/limited")
		@RateLimited(RateLimitPolicy.USER_SEARCH)
		String limited() {
			return "ok";
		}

		@GetMapping("/open")
		String open() {
			return "ok";
		}
	}
}
