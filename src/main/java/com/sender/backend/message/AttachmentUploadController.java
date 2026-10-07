package com.sender.backend.message;

import com.sender.backend.common.ratelimit.RateLimitPolicy;
import com.sender.backend.common.ratelimit.RateLimited;
import com.sender.backend.message.MessageDtos.AttachmentUploadRequest;
import com.sender.backend.message.MessageDtos.AttachmentUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/attachment-uploads")
@Tag(name = "Messages", description = "Persistent messages and attachments")
@SecurityRequirement(name = "bearerAuth")
public class AttachmentUploadController {
    private final MessageService service;

    public AttachmentUploadController(MessageService service) {
        this.service = service;
    }

    @PostMapping
    @RateLimited(RateLimitPolicy.ATTACHMENT_UPLOAD)
    @Operation(summary = "Create message attachment upload URL",
            description = "Returns a short-lived presigned PUT URL. The client uploads bytes, then commits the object key when sending a message.")
    @ApiResponse(responseCode = "200", description = "Upload URL created")
    @ApiResponse(responseCode = "400", description = "Unsupported type, invalid filename, or missing size")
    @ApiResponse(responseCode = "403", description = "Authenticated user is not a member")
    @ApiResponse(responseCode = "404", description = "Conversation not found")
    @ApiResponse(responseCode = "413", description = "File exceeds the 10MB limit")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public AttachmentUploadResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer conversationId,
            @Valid @RequestBody AttachmentUploadRequest request) {
        return service.createAttachmentUpload(Integer.valueOf(jwt.getSubject()), conversationId, request);
    }
}
