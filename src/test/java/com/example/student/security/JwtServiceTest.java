package com.example.student.security;

import com.example.student.model.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    final JwtService service = new JwtService();
    @Test void signedTokenPreservesIdentityAndRoles() {
        String token = service.generateToken("admin@example.com", Set.of(Role.ADMIN, Role.TEACHER));
        assertEquals("admin@example.com", service.extractUsername(token));
        assertEquals(Set.of(Role.ADMIN, Role.TEACHER), service.extractRoles(token));
        assertTrue(service.validateToken(token, User.withUsername("admin@example.com").password("unused").roles("ADMIN").build()));
        assertFalse(service.validateToken(token, User.withUsername("other@example.com").password("unused").roles("ADMIN").build()));
    }
    @Test void rejectsMalformedToken() {
        assertThrows(JwtException.class, () -> service.extractUsername("not.a.jwt"));
    }
    @Test void rejectsModifiedSignature() {
        String token = service.generateToken("admin@example.com", Set.of(Role.ADMIN));
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertThrows(JwtException.class, () -> service.extractUsername(String.join(".", parts)));
    }
}
