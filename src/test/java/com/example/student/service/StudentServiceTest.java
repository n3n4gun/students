package com.example.student.service;

import com.example.student.dto.*;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.mapper.StudentMapperImpl;
import com.example.student.model.Student;
import com.example.student.repository.StudentRepository;
import com.example.student.service.impl.StudentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentServiceTest {
    StudentRepository repository;
    StudentServiceImpl service;
    Student student;

    @BeforeEach void setup() {
        repository = mock(StudentRepository.class);
        service = new StudentServiceImpl(repository, new StudentMapperImpl());
        student = new Student();
        student.setId(1L);
        student.setName("Ivan");
        student.setSurname("Ivanov");
        student.setEmail("old@example.com");
        student.setPhoneNumber("123");
    }

    @Test void savesAllFieldsAndReturnsAssignedId() {
        when(repository.save(any())).thenAnswer(invocation -> {
            Student saved = invocation.getArgument(0);
            assertEquals("Ivan", saved.getName());
            assertEquals("Ivanov", saved.getSurname());
            assertEquals("123", saved.getPhoneNumber());
            assertEquals("old@example.com", saved.getEmail());
            saved.setId(1L);
            return saved;
        });
        StudentResponse result = service.save(new StudentRequest("Ivan", "Ivanov", "123", "old@example.com"));
        assertEquals(1L, result.getId());
        assertEquals("Ivanov", result.getSurname());
    }

    @Test void returnsFullDetails() {
        when(repository.findById(1L)).thenReturn(Optional.of(student));
        StudentFullResponse result = service.findById(1L);
        assertAll(() -> assertEquals(1L, result.getId()),
                () -> assertEquals("Ivan", result.getName()),
                () -> assertEquals("Ivanov", result.getSurname()),
                () -> assertEquals("123", result.getPhoneNumber()),
                () -> assertEquals("old@example.com", result.getEmail()));
    }

    @Test void listsStudentsAndHandlesEmptyDatabase() {
        when(repository.findAll()).thenReturn(List.of(student), List.of());
        StudentResponseList result = service.findAll();
        assertEquals(1, result.getTotalCount());
        assertEquals("Ivan", result.getResponse().getFirst().getName());
        assertEquals(1L, result.getResponse().getFirst().getId());
        assertEquals("Ivanov", result.getResponse().getFirst().getSurname());
        assertTrue(service.findAll().getResponse().isEmpty());
    }

    @Test void updatesContactsWithoutChangingName() {
        when(repository.findById(1L)).thenReturn(Optional.of(student));
        when(repository.save(student)).thenReturn(student);
        service.update(1L, new StudentUpdateRequest("456", "new@example.com"));
        assertEquals("456", student.getPhoneNumber());
        assertEquals("new@example.com", student.getEmail());
        assertEquals("Ivan", student.getName());
        verify(repository).save(student);
    }

    @Test void nullUpdateFieldsPreserveExistingContacts() {
        when(repository.findById(1L)).thenReturn(Optional.of(student));
        when(repository.save(student)).thenReturn(student);
        service.update(1L, new StudentUpdateRequest());
        assertEquals("123", student.getPhoneNumber());
        assertEquals("old@example.com", student.getEmail());
    }

    @Test void missingStudentCannotBeReadOrUpdated() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.findById(99L));
        assertThrows(EntityNotFoundException.class, () -> service.update(99L, new StudentUpdateRequest()));
        verify(repository, never()).save(any());
    }

    @Test void deletesRequestedStudent() {
        service.deleteById(1L);
        verify(repository).deleteById(1L);
    }
}
