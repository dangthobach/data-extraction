package com.extraction.integration.security;

import com.extraction.integration.dto.SystemInfo;
import com.extraction.integration.service.IamAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final IamAuthService iamAuthService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token != null) {
                // Validate token via IAM Service (cached)
                SystemInfo systemInfo = iamAuthService.validate(token);

                // Create UserDetails based on SystemInfo
                // We use SystemId as username, and assign a default ROLE_SYSTEM
                User principal = new User(
                        systemInfo.getSystemId(),
                        "",
                        Collections.singletonList(new SimpleGrantedAuthority("ROLE_SYSTEM")));

                // Create Authentication Token
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        systemInfo, // Set SystemInfo as principal properly for Controller access
                        null,
                        principal.getAuthorities());

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set Authentication in Context
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Could not set user authentication in security context", e);
            // Don't throw exception here, let the Security Chain handle the unauthenticated
            // state
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
