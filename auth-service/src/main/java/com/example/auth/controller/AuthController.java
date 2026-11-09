package com.example.auth.controller;

import com.example.auth.config.JwtUtil;
import com.example.auth.dto.ApiResponse;
import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.TokenValidationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for login and token validation")
public class AuthController {

    private final JwtUtil jwtUtil;

    // In-memory mock user credentials for SOA demonstration
    private static final Map<String, UserRecord> USERS = Map.of(
            "admin", new UserRecord("admin123", List.of("ROLE_ADMIN", "ROLE_USER")),
            "john_doe", new UserRecord("password123", List.of("ROLE_USER")),
            "customer1", new UserRecord("customer123", List.of("ROLE_USER"))
    );

    public record UserRecord(String password, List<String> roles) {}

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    @Operation(summary = "Login and generate JWT token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        UserRecord user = USERS.get(request.getUsername());

        if (user == null || !user.password().equals(request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "Invalid username or password"));
        }

        String token = jwtUtil.generateToken(request.getUsername(), user.roles());
        AuthResponse response = AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .username(request.getUsername())
                .roles(user.roles())
                .expiresInMs(jwtUtil.getExpirationMs())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @GetMapping("/validate")
    @Operation(summary = "Validate JWT Bearer Token (SOA Inter-service authentication)")
    public ResponseEntity<ApiResponse<TokenValidationResponse>> validateToken(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String paramToken) {

        String token = null;
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (StringUtils.hasText(paramToken)) {
            token = paramToken;
        }

        if (!StringUtils.hasText(token) || !jwtUtil.isTokenValid(token)) {
            TokenValidationResponse invalidResponse = TokenValidationResponse.builder()
                    .valid(false)
                    .message("Token is invalid, missing, or expired")
                    .build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "Token verification failed"));
        }

        String username = jwtUtil.getUsernameFromToken(token);
        List<String> roles = jwtUtil.getRolesFromToken(token);

        TokenValidationResponse validationResponse = TokenValidationResponse.builder()
                .valid(true)
                .username(username)
                .roles(roles)
                .message("Token is valid")
                .build();

        return ResponseEntity.ok(ApiResponse.success(validationResponse, "Token validated successfully"));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user info from Bearer token")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "Missing or invalid Authorization header"));
        }

        String token = authHeader.substring(7);
        if (!jwtUtil.isTokenValid(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "Token is expired or invalid"));
        }

        String username = jwtUtil.getUsernameFromToken(token);
        List<String> roles = jwtUtil.getRolesFromToken(token);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "username", username,
                "roles", roles
        ), "User details retrieved successfully"));
    }

    @GetMapping("/health")
    @Operation(summary = "Service Health Check")
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("status", "UP", "service", "auth-service", "port", "8081"), "Auth service is running"));
    }
}

