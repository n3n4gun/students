package com.example.student.security;

import com.example.student.model.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import jakarta.servlet.FilterChain;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtFilterTest {
    final JwtService jwt = new JwtService();
    final UserDetailsService users = mock(UserDetailsService.class);
    final JwtFilter filter = new JwtFilter(users, jwt);
    final FilterChain chain = mock(FilterChain.class);
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void anonymousRequestContinuesWithoutAuthentication() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/student");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
        verifyNoInteractions(users);
    }
    @Test void validBearerAuthenticatesWithDatabaseAuthorities() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/student");
        var response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer " + jwt.generateToken("teacher@example.com", Set.of(Role.TEACHER)));
        when(users.loadUserByUsername("teacher@example.com")).thenReturn(
                User.withUsername("teacher@example.com").password("encoded").roles("TEACHER").build());
        filter.doFilter(request, response, chain);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("teacher@example.com", auth.getName());
        assertTrue(auth.isAuthenticated());
        assertEquals("ROLE_TEACHER", auth.getAuthorities().iterator().next().getAuthority());
        verify(chain).doFilter(request, response);
    }
    @Test void invalidTokenReturnsUnauthorizedWithoutCallingController() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/student");
        var response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer invalid");
        filter.doFilter(request, response, chain);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("error"));
        verifyNoInteractions(chain);
    }
}
