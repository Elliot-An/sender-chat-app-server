package com.sender.backend.user;

import com.sender.backend.auth.AuthDtos.PublicUser;
import com.sender.backend.common.ratelimit.RateLimitPolicy;
import com.sender.backend.common.ratelimit.RateLimited;
import com.sender.backend.friendship.FriendshipDtos.UserSummary;
import com.sender.backend.user.UserDtos.AvatarUploadRequest;
import com.sender.backend.user.UserDtos.AvatarUploadResponse;
import com.sender.backend.user.UserDtos.UpdateProfileRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Authenticated user discovery and profile endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserRepository users;
    private final UserService userService;

    public UserController(UserRepository users, UserService userService) {
        this.users = users;
        this.userService = userService;
    }

    @GetMapping("/search")
    @RateLimited(RateLimitPolicy.USER_SEARCH)
    @Operation(summary = "Search users", description = "Searches usernames and display names. Queries shorter than two characters return no results.")
    @ApiResponse(responseCode = "200", description = "Matching users returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public List<UserSummary> search(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Username or display name fragment. Minimum two characters.", example = "lan")
            @RequestParam(defaultValue = "") String q) {
        String query = q.trim().toLowerCase();
        if (query.length() < 2) return List.of();
        return users.search(Integer.valueOf(jwt.getSubject()), query, PageRequest.of(0, 20)).stream()
                .map(UserSummary::from)
                .toList();
    }

    @PostMapping("/me/avatar-uploads")
    @RateLimited(RateLimitPolicy.AVATAR_UPLOAD)
    @Operation(summary = "Create avatar upload URL", description = "Returns a short-lived presigned PUT URL. The client uploads bytes directly to object storage, then commits the object key.")
    @ApiResponse(responseCode = "200", description = "Upload URL created")
    @ApiResponse(responseCode = "400", description = "Unsupported image type or missing size")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @ApiResponse(responseCode = "413", description = "Image exceeds the 2MB limit")
    @ApiResponse(responseCode = "429", description = "Too many upload requests")
    public AvatarUploadResponse createAvatarUpload(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AvatarUploadRequest request) {
        Integer userId = Integer.valueOf(jwt.getSubject());
        return userService.createAvatarUpload(userId, request);
    }

    @PatchMapping("/me")
    @RateLimited(RateLimitPolicy.PROFILE_UPDATE)
    @Operation(summary = "Update current profile", description = "Updates the authenticated user's display name and/or commits a previously uploaded avatar object key. Username cannot be changed.")
    @ApiResponse(responseCode = "200", description = "Profile updated")
    @ApiResponse(responseCode = "400", description = "Invalid display name or avatar object key")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "429", description = "Too many profile updates")
    public PublicUser updateMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        Integer userId = Integer.valueOf(jwt.getSubject());
        return userService.updateProfile(userId, request);
    }
}
