package com.sender.backend.friendship;

import com.sender.backend.user.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class FriendshipDtos {
    private FriendshipDtos() {}

    public record UserSummary(Integer id, String username, String displayName, String avatarUrl, boolean online) {
        public static UserSummary from(User user) {
            return from(user, false);
        }

        public static UserSummary from(User user, boolean online) {
            return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl(), online);
        }
    }

    public record FriendshipResponse(Integer id, UserSummary requester, UserSummary addressee,
                                     Friendship.Status status, Instant createdAt) {
        public static FriendshipResponse from(Friendship friendship) {
            return from(friendship, false, false);
        }

        public static FriendshipResponse from(Friendship friendship, boolean requesterOnline, boolean addresseeOnline) {
            return new FriendshipResponse(friendship.getId(),
                    UserSummary.from(friendship.getRequester(), requesterOnline),
                    UserSummary.from(friendship.getAddressee(), addresseeOnline),
                    friendship.getStatus(), friendship.getCreatedAt());
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
