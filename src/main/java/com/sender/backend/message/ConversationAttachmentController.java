package com.sender.backend.message;

import com.sender.backend.common.ratelimit.RateLimitPolicy;
import com.sender.backend.common.ratelimit.RateLimited;
import com.sender.backend.message.MessageDtos.AttachmentPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/attachments")
@Tag(name = "Messages", description = "Persistent messages and attachments")
@SecurityRequirement(name = "bearerAuth")
public class ConversationAttachmentController {
    private final MessageService service;

    public ConversationAttachmentController(MessageService service) {
        this.service = service;
    }

    @GetMapping
    @RateLimited(RateLimitPolicy.ATTACHMENT_LIST)
    @Operation(summary = "List conversation attachments",
            description = "Returns opaque cursor-paginated attachments for a conversation. "
                    + "kind filters to all, media (image/*), or files (non-image). Download URLs are not included.")
    @ApiResponse(responseCode = "200", description = "Attachments returned")
    @ApiResponse(responseCode = "400", description = "Invalid kind or cursor")
    @ApiResponse(responseCode = "403", description = "Authenticated user is not a member")
    @ApiResponse(responseCode = "404", description = "Conversation not found")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public AttachmentPage list(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable Integer conversationId,
                               @RequestParam(defaultValue = "all") String kind,
                               @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "30") int limit) {
        return service.listAttachments(Integer.valueOf(jwt.getSubject()), conversationId, kind, cursor, limit);
    }
}
