package com.sender.backend.conversation;

import com.sender.backend.conversation.ConversationDtos.*;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Direct and group conversation membership and summaries")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {
    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @PostMapping("/direct")
    @Operation(summary = "Create or get a direct conversation")
    @ApiResponse(responseCode = "200", description = "Direct conversation returned")
    @ApiResponse(responseCode = "403", description = "Users are not accepted friends")
    public ConversationResponse direct(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DirectRequest request) {
        return service.direct(userId(jwt), request);
    }

    @PostMapping("/group")
    @Operation(summary = "Create a group conversation")
    @ApiResponse(responseCode = "201", description = "Group conversation created")
    @ApiResponse(responseCode = "403", description = "A member is not an accepted friend")
    public ResponseEntity<ConversationResponse> group(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.group(userId(jwt), request));
    }

    @GetMapping
    @Operation(summary = "List conversations", description = "Lists the authenticated user's summaries by latest activity.")
    @ApiResponse(responseCode = "200", description = "Conversation summaries returned")
    public Page<Summary> list(@AuthenticationPrincipal Jwt jwt,
                              @RequestParam(required = false) String cursor,
                              @RequestParam(defaultValue = "30") int limit) {
        return service.list(userId(jwt), cursor, limit);
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get conversation metadata")
    @ApiResponse(responseCode = "200", description = "Conversation metadata returned")
    @ApiResponse(responseCode = "403", description = "Authenticated user is not a member")
    public ConversationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer conversationId) {
        return service.get(userId(jwt), conversationId);
    }

    @PatchMapping("/{conversationId}")
    @Operation(summary = "Update group name and/or avatar")
    @ApiResponse(responseCode = "200", description = "Group updated")
    @ApiResponse(responseCode = "400", description = "Invalid name, avatar key, or not a group")
    @ApiResponse(responseCode = "403", description = "Authenticated user is not a member")
    public ConversationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer conversationId,
                                       @Valid @RequestBody UpdateGroupRequest request) {
        return service.updateGroup(userId(jwt), conversationId, request);
    }

    @PostMapping("/{conversationId}/avatar-uploads")
    @Operation(summary = "Create group avatar upload URL",
            description = "Returns a short-lived presigned PUT URL for a group avatar. The client uploads bytes, then commits the object key via PATCH.")
    @ApiResponse(responseCode = "200", description = "Upload URL created")
    @ApiResponse(responseCode = "400", description = "Unsupported image type, missing size, or not a group")
    @ApiResponse(responseCode = "403", description = "Authenticated user is not a member")
    @ApiResponse(responseCode = "413", description = "Image exceeds the 2MB limit")
    public AvatarUploadResponse createAvatarUpload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer conversationId,
            @Valid @RequestBody AvatarUploadRequest request) {
        return service.createGroupAvatarUpload(userId(jwt), conversationId, request);
    }

    @PostMapping("/{conversationId}/members")
    @Operation(summary = "Add a friend to a group")
    @ApiResponse(responseCode = "200", description = "Member added")
    @ApiResponse(responseCode = "403", description = "Not a member or target is not an accepted friend")
    public ConversationResponse add(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer conversationId,
                                    @Valid @RequestBody MemberRequest request) {
        return service.addMember(userId(jwt), conversationId, request);
    }

    @DeleteMapping("/{conversationId}/members/{memberId}")
    @Operation(summary = "Remove a group member or leave the group",
            description = "Leaving as the last member dissolves the group. Returns 204 when the caller leaves or the group is dissolved.")
    @ApiResponse(responseCode = "200", description = "Member removed; updated conversation returned")
    @ApiResponse(responseCode = "204", description = "Caller left or group dissolved")
    @ApiResponse(responseCode = "403", description = "Authenticated user is not a member")
    public ResponseEntity<ConversationResponse> remove(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable Integer conversationId,
                                                       @PathVariable Integer memberId) {
        return service.removeMember(userId(jwt), conversationId, memberId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private Integer userId(Jwt jwt) {
        return Integer.valueOf(jwt.getSubject());
    }
}
