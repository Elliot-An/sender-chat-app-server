package com.sender.backend.conversation;

import com.sender.backend.auth.AuthDtos.PublicUser;
import com.sender.backend.message.Message;
import com.sender.backend.user.User;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ConversationDtos {
    private ConversationDtos() {}

    public record DirectRequest(@NotNull Integer otherUserId) {}

    public record GroupRequest(
            @NotBlank @Size(max = 100) String name,
            @NotEmpty @Size(max = 50) Set<@NotNull Integer> memberIds) {}

    public record MemberRequest(@NotNull Integer userId) {}

    public record MemberResponse(Integer userId, String username, String displayName, Instant joinedAt) {
        static MemberResponse from(ConversationMember member) {
            User user = member.getUser();
            return new MemberResponse(user.getId(), user.getUsername(), user.getDisplayName(), member.getJoinedAt());
        }
    }

    public record ConversationResponse(Integer id, Conversation.Type type, String name,
                                       Instant createdAt, Instant updatedAt, List<MemberResponse> members) {
        static ConversationResponse from(Conversation conversation, List<ConversationMember> members) {
            return new ConversationResponse(conversation.getId(), conversation.getType(), conversation.getName(),
                    conversation.getCreatedAt(), conversation.getUpdatedAt(), members.stream().map(MemberResponse::from).toList());
        }
    }

    public record LatestMessage(Long id, String body, PublicUser sender, Instant createdAt, UUID clientMessageId) {
        static LatestMessage from(Message message) {
            return new LatestMessage(message.getId(), message.getBody(), PublicUser.from(message.getSender()),
                    message.getCreatedAt(), message.getClientMessageId());
        }
    }

    public record Summary(Integer id, Conversation.Type type, String name, PublicUser otherUser,
                          LatestMessage latestMessage, int unreadCount, Instant updatedAt) {}

    public record Page<T>(List<T> items, String nextCursor, boolean hasMore) {}
}
