package com.sender.backend.friendship;

import com.sender.backend.common.ratelimit.RateLimitPolicy;
import com.sender.backend.common.ratelimit.RateLimited;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import static com.sender.backend.friendship.FriendshipDtos.*;

@RestController
@RequestMapping("/api/v1/friendships")
@Tag(name = "Friendships", description = "Send and manage authenticated user friendship requests")
@SecurityRequirement(name = "bearerAuth")
public class FriendshipController {
    private final FriendshipService service;

    public FriendshipController(FriendshipService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List accepted friendships")
    @ApiResponse(responseCode = "200", description = "Accepted friendships returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    public List<FriendshipResponse> accepted(@AuthenticationPrincipal Jwt jwt) {
        return service.accepted(userId(jwt));
    }

    @GetMapping("/requests")
    @Operation(summary = "List incoming friendship requests")
    @ApiResponse(responseCode = "200", description = "Incoming requests returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    public List<FriendshipResponse> requests(@AuthenticationPrincipal Jwt jwt) {
        return service.incomingRequests(userId(jwt));
    }

    @PostMapping
    @RateLimited(RateLimitPolicy.FRIENDSHIP_WRITE)
    @Operation(summary = "Send a friendship request", description = "Creates a pending request for another user.")
    @ApiResponse(responseCode = "201", description = "Friendship request created")
    @ApiResponse(responseCode = "400", description = "Invalid user or self-request")
    @ApiResponse(responseCode = "409", description = "A friendship already exists")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public ResponseEntity<FriendshipResponse> request(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(userId(jwt), request.userId()));
    }

    @PatchMapping("/{friendshipId}")
    @RateLimited(RateLimitPolicy.FRIENDSHIP_WRITE)
    @Operation(summary = "Accept or decline a friendship request")
    @ApiResponse(responseCode = "200", description = "Friendship request updated")
    @ApiResponse(responseCode = "403", description = "The authenticated user cannot decide this request")
    @ApiResponse(responseCode = "404", description = "Friendship request not found")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public FriendshipResponse decide(
                                     @AuthenticationPrincipal Jwt jwt,
                                     @Parameter(description = "Friendship request identifier", example = "42")
                                     @PathVariable Integer friendshipId,
                                     @Valid @RequestBody DecisionRequest request) {
        return service.decide(userId(jwt), friendshipId, request.decision());
    }

    private Integer userId(Jwt jwt) {
        return Integer.valueOf(jwt.getSubject());
    }
}
