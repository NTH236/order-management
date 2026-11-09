package com.example.order.security;

import com.example.order.client.AuthServiceClient;
import com.example.order.dto.ApiResponse;
import com.example.order.dto.TokenValidationResponse;
import com.example.order.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class AuthFilter extends OncePerRequestFilter {

    private final AuthServiceClient authServiceClient;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs",
            "/swagger-resources/**",
            "/webjars/**",
            "/h2-console/**",
            "/orders/health",
            "/favicon.ico"
    );

    public AuthFilter(AuthServiceClient authServiceClient, ObjectMapper objectMapper) {
        this.authServiceClient = authServiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header. A valid Bearer token is required.");
            return;
        }

        String token = authHeader.substring(7).trim();

        try {
            TokenValidationResponse validationResponse = authServiceClient.validateToken(token);

            if (validationResponse == null || !validationResponse.isValid()) {
                String message = (validationResponse != null && StringUtils.hasText(validationResponse.getMessage()))
                        ? validationResponse.getMessage()
                        : "Invalid or expired token.";
                writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Unauthorized: " + message);
                return;
            }

            // Set user into thread context
            UserContext.setCurrentUser(new UserContext.UserInfo(
                    validationResponse.getUsername(),
                    validationResponse.getRoles()
            ));

            request.setAttribute("authenticatedUser", validationResponse.getUsername());
            request.setAttribute("authenticatedRoles", validationResponse.getRoles());

            filterChain.doFilter(request, response);

        } catch (ServiceUnavailableException ex) {
            writeErrorResponse(response, HttpStatus.SERVICE_UNAVAILABLE, "Authentication Service is currently unavailable. Please try again later.");
        } finally {
            UserContext.clear();
        }
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.error(status.value(), message);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}

