package com.sender.backend.user;

import com.sender.backend.friendship.FriendshipDtos.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Authenticated user discovery endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/search")
    @Operation(summary = "Search users", description = "Searches usernames and display names. Queries shorter than two characters return no results.")
    @ApiResponse(responseCode = "200", description = "Matching users returned")
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
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
}
