package com.sender.backend.friendship;

import com.sender.backend.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class FriendshipDtos {
    private FriendshipDtos() {}

    public record UserSummary(Integer id, String username, String displayName, String avatarUrl) {
        public static UserSummary from(User user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
        }
    }

    public record FriendshipResponse(Integer id, UserSummary requester, UserSummary addressee,
                                     Friendship.Status status, Instant createdAt) {
        public static FriendshipResponse from(Friendship friendship) {
            return new FriendshipResponse(friendship.getId(), UserSummary.from(friendship.getRequester()),
                    UserSummary.from(friendship.getAddressee()), friendship.getStatus(), friendship.getCreatedAt());
        }
    }

    public record CreateRequest(
            @Schema(description = "User receiving the friendship request", example = "17")
            @NotNull Integer userId) {}
    public record DecisionRequest(
            @Schema(description = "Decision for the pending request", example = "ACCEPT")
            @NotNull Decision decision) {}
    public enum Decision { ACCEPT, DECLINE }
}
