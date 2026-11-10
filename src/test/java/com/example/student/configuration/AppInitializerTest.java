package com.example.student.configuration;

import com.example.student.model.*;
import com.example.student.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppInitializerTest {
    final UserRepository users = mock(UserRepository.class);
    final StudentRepository students = mock(StudentRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final AppInitializer initializer = new AppInitializer(users, encoder, students);
    @Test void emptyDatabaseReceivesThreeAccountsAndFiveStudents() {
        when(encoder.encode(anyString())).thenAnswer(i -> "encoded:" + i.getArgument(0));
        initializer.init();
        var accounts = ArgumentCaptor.forClass(User.class);
        verify(users, times(3)).save(accounts.capture());
        assertEquals(3, accounts.getAllValues().stream().map(User::getEmail).distinct().count());
        assertTrue(accounts.getAllValues().stream().allMatch(user -> user.getPassword().startsWith("encoded:")));
        assertEquals(java.util.Set.of(Role.SUPER_ADMIN, Role.ADMIN, Role.TEACHER),
                accounts.getAllValues().stream().flatMap(user -> user.getRoles().stream()).collect(java.util.stream.Collectors.toSet()));
        var records = ArgumentCaptor.forClass(Student.class);
        verify(students, times(5)).save(records.capture());
        assertEquals(5, records.getAllValues().stream().map(Student::getName).distinct().count());
        assertTrue(records.getAllValues().stream().allMatch(student -> student.getSurname() != null));
    }
    @Test void existingDatabaseIsNotSeededAgain() {
        when(users.existsByEmail(anyString())).thenReturn(true);
        when(students.count()).thenReturn(5L);
        initializer.init();
        verify(users, never()).save(any());
        verify(students, never()).save(any());
        verifyNoInteractions(encoder);
    }
}
