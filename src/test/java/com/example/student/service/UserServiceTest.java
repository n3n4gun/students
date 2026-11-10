package com.example.student.service;

import com.example.student.dto.user.*;
import com.example.student.exception.*;
import com.example.student.mapper.UserMapperImpl;
import com.example.student.model.*;
import com.example.student.repository.UserRepository;
import com.example.student.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    UserRepository repository;
    PasswordEncoder encoder;
    UserServiceImpl service;
    UserRequest request;
    @BeforeEach void setup() {
        repository = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new UserServiceImpl(repository, new UserMapperImpl(), encoder);
        request = new UserRequest("user@example.com", "secret", Set.of(Role.TEACHER));
    }
    @Test void createsUserWithEncodedPassword() {
        when(encoder.encode("secret")).thenReturn("encoded");
        when(repository.save(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            assertEquals("encoded", user.getPassword());
            user.setId(7L);
            return user;
        });
        UserResponse result = service.save(request);
        assertEquals(7L, result.getId());
        assertEquals(request.getEmail(), result.getEmail());
        assertEquals(Set.of(Role.TEACHER), result.getRoles());
    }
    @Test void rejectsDuplicateEmailWithoutSaving() {
        when(repository.existsByEmail(request.getEmail())).thenReturn(true);
        assertThrows(EmailAlreadyExistsException.class, () -> service.save(request));
        assertFalse(service.register(request));
        verify(repository, never()).save(any());
        verifyNoInteractions(encoder);
    }
    @Test void registersAndEncodesPassword() {
        when(encoder.encode("secret")).thenReturn("encoded");
        assertTrue(service.register(request));
        verify(repository).save(argThat(user -> "encoded".equals(user.getPassword())
                && user.getRoles().equals(Set.of(Role.TEACHER))));
    }
    @Test void registrationRequiresEmail() {
        request.setEmail(null);
        assertFalse(service.register(request));
        verifyNoInteractions(repository, encoder);
    }
    @Test void blankPasswordIsNotEncoded() {
        request.setPassword(" ");
        assertTrue(service.register(request));
        verifyNoInteractions(encoder);
        verify(repository).save(argThat(user -> " ".equals(user.getPassword())));
    }
    @Test void nullPasswordIsNotEncoded() {
        request.setPassword(null);
        assertTrue(service.register(request));
        verifyNoInteractions(encoder);
    }
    @Test void looksUpExistingUserAndRejectsMissingUser() {
        User user = new User();
        user.setEmail(request.getEmail());
        when(repository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        assertSame(user, service.findByEmail(request.getEmail()));
        assertThrows(EntityNotFoundException.class, () -> service.findByEmail("missing@example.com"));
    }
    @Test void listsUsersWithoutExposingPasswords() {
        User user = new User();
        user.setId(1L);
        user.setEmail(request.getEmail());
        user.setRoles(request.getRoles());
        when(repository.findAll()).thenReturn(List.of(user), List.of());
        UserResponseList result = service.findAll();
        assertEquals(1, result.getTotalCount());
        assertEquals(request.getEmail(), result.getResponses().getFirst().getEmail());
        assertTrue(service.findAll().getResponses().isEmpty());
    }
    @Test void checksEmailExistenceAndDeletesUser() {
        when(repository.existsByEmail(request.getEmail())).thenReturn(true);
        assertTrue(service.existsByEmail(request.getEmail()));
        assertFalse(service.existsByEmail("missing"));
        service.deleteById(5L);
        verify(repository).deleteById(5L);
    }
}
