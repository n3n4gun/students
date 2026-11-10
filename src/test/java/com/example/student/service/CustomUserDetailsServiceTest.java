package com.example.student.service;

import com.example.student.model.*;
import com.example.student.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomUserDetailsServiceTest {
    final UserRepository repository = mock(UserRepository.class);
    final CustomUserDetailsService service = new CustomUserDetailsService(repository);
    @Test void loadsPasswordAndAllRoles() {
        User user = new User();
        user.setEmail("admin@example.com");
        user.setPassword("encoded");
        user.setRoles(Set.of(Role.ADMIN, Role.TEACHER));
        when(repository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        var details = service.loadUserByUsername(user.getEmail());
        assertEquals(user.getEmail(), details.getUsername());
        assertEquals("encoded", details.getPassword());
        assertEquals(Set.of("ROLE_ADMIN", "ROLE_TEACHER"),
                details.getAuthorities().stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(details.isEnabled());
        assertTrue(details.isAccountNonLocked());
    }
    @Test void missingUserCannotAuthenticate() {
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing"));
    }
}
