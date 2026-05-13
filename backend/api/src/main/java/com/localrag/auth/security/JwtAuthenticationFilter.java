package com.localrag.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register", "/api/auth/login", "/api/health"
    );

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            // 开发模式：未带 Token 时以 anonymous 身份放行
            setAnonymousContext(request);
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            JwtUtils.JwtPayload payload = jwtUtils.validate(token);
            request.setAttribute("userId", payload.userId());
            request.setAttribute("username", payload.username());
            request.setAttribute("role", payload.role());

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + payload.role()));
            var auth = new UsernamePasswordAuthenticationToken(
                    payload.username(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

            chain.doFilter(request, response);
        } catch (Exception e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            sendUnauthorized(response, "Token无效或已过期");
        }
    }

    private void setAnonymousContext(HttpServletRequest request) {
        request.setAttribute("userId", "anonymous");
        request.setAttribute("username", "anonymous");
        request.setAttribute("role", "USER");
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var auth = new UsernamePasswordAuthenticationToken("anonymous", null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", 401, "message", message)));
    }
}
