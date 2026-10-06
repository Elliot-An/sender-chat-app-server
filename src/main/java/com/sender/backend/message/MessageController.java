package com.sender.backend.message;

import com.sender.backend.message.MessageDtos.*;
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
@RequestMapping("/api/v1/conversations/{conversationId}/messages")
@Tag(name = "Messages", description = "Persistent text messages")
@SecurityRequirement(name = "bearerAuth")
public class MessageController {
    private final MessageService service;

    public MessageController(MessageService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List conversation messages", description = "Returns opaque cursor pagination ordered by createdAt and id.")
    @ApiResponse(responseCode = "200", description = "Messages returned")
    @ApiResponse(responseCode = "403", description = "Authenticated user is not a member")
    public Page messages(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer conversationId,
                         @RequestParam(required = false) String cursor,
                         @RequestParam(defaultValue = "50") int limit) {
        return service.list(userId(jwt), conversationId, cursor, limit);
    }

    @PostMapping
    @Operation(summary = "Send a text message", description = "Retries with the same clientMessageId are idempotent.")
    @ApiResponse(responseCode = "201", description = "Message persisted")
    @ApiResponse(responseCode = "403", description = "Authenticated user is not a member")
    @ApiResponse(responseCode = "409", description = "Client message ID conflicts with a different body")
    public ResponseEntity<MessageResponse> send(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer conversationId,
                                                @Valid @RequestBody SendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.send(userId(jwt), conversationId, request));
    }

    @GetMapping("/search")
    @Operation(summary = "Search conversation messages", description = "Returns ranked, cursor-paginated messages scoped to the conversation.")
    @ApiResponse(responseCode = "200", description = "Search results returned")
    @ApiResponse(responseCode = "400", description = "Invalid query or cursor")
    @ApiResponse(responseCode = "403", description = "Authenticated user is not a member")
    public SearchPage search(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer conversationId,
                                            @RequestParam String q,
                                            @RequestParam(required = false) String cursor,
                                            @RequestParam(defaultValue = "30") int limit) {
        return service.search(userId(jwt), conversationId, q, cursor, limit);
    }

    private Integer userId(Jwt jwt) {
        return Integer.valueOf(jwt.getSubject());
    }
}
