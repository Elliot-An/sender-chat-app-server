package com.sender.backend.common.ratelimit;

/**
 * Named rate-limit buckets. The maximum number of hits per window for each policy is
 * configured under {@code app.rate-limit.max-hits.<policy-name>} (sourced from {@code .env}).
 */
public enum RateLimitPolicy {
	/** Register and login: unauthenticated, so keyed by client address. */
	AUTH_CREDENTIALS(Scope.CLIENT_ADDRESS),
	/** Refresh and logout: cookie based, so keyed by client address. */
	AUTH_SESSION(Scope.CLIENT_ADDRESS),
	PASSWORD_CHANGE(Scope.USER),
	USER_SEARCH(Scope.USER),
	PROFILE_UPDATE(Scope.USER),
	AVATAR_UPLOAD(Scope.USER),
	FRIENDSHIP_WRITE(Scope.USER),
	CONVERSATION_WRITE(Scope.USER),
	MESSAGE_SEND(Scope.USER),
	MESSAGE_SEARCH(Scope.USER),
	MESSAGE_PROGRESS(Scope.USER),
	ATTACHMENT_UPLOAD(Scope.USER),
	ATTACHMENT_DOWNLOAD(Scope.USER),
	ATTACHMENT_LIST(Scope.USER),
	TYPING(Scope.USER),
	PRESENCE_HEARTBEAT(Scope.USER);

	public enum Scope {
		/** Authenticated user id, falling back to client address when unauthenticated. */
		USER,
		/** Always the remote client address. */
		CLIENT_ADDRESS
	}

	private final Scope scope;

	RateLimitPolicy(Scope scope) {
		this.scope = scope;
	}

	public Scope scope() {
		return scope;
	}
}
