package com.example.student.controller;

import com.example.student.configuration.SecurityConfiguration;
import com.example.student.dto.*;
import com.example.student.dto.user.*;
import com.example.student.exception.EntityNotFoundException;
import com.example.student.model.Role;
import com.example.student.security.*;
import com.example.student.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Set;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({StudentController.class, UserController.class, AuthController.class})
@Import({SecurityConfiguration.class, JwtFilter.class, JwtService.class})
class ApiSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean StudentService students;
    @MockitoBean UserService users;
    @MockitoBean UserDetailsService details;

    @Test void anonymousCannotReadProtectedApi() throws Exception {
        mvc.perform(get("/api/v1/student")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
        verifyNoInteractions(students, users);
    }
    @Test void realLoginIssuesUsableJwt() throws Exception {
        when(details.loadUserByUsername("admin@example.com")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("admin@example.com")
                        .password(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("secret"))
                        .roles("ADMIN").build());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"admin@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
    }
    @Test void invalidLoginPayloadIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"invalid\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void bearerTokenCanReadStudents() throws Exception {
        when(details.loadUserByUsername("teacher@example.com")).thenReturn(
                org.springframework.security.core.userdetails.User.withUsername("teacher@example.com").password("encoded").roles("TEACHER").build());
        when(students.findAll()).thenReturn(new StudentResponseList(List.of(), 0));
        mvc.perform(get("/api/v1/student").header("Authorization", "Bearer " +
                        jwt.generateToken("teacher@example.com", Set.of(Role.TEACHER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(0));
    }
    @Test @WithMockUser(roles = "TEACHER")
    void teacherReadsDetailsButCannotModifyStudentsOrUsers() throws Exception {
        when(students.findById(1L)).thenReturn(new StudentFullResponse(1, "Ivan", "Ivanov", "123", "i@example.com"));
        mvc.perform(get("/api/v1/student/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("123"));
        mvc.perform(get("/api/v1/student/info/1")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/student/1")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/student/save").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/student/1").contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
        verify(students, never()).deleteById(anyLong());
        verify(students, never()).save(any());
        verify(students, never()).update(anyLong(), any());
        verifyNoInteractions(users);
    }
    @Test @WithMockUser(roles = "ADMIN")
    void adminCreatesUpdatesAndDeletesStudent() throws Exception {
        when(students.save(any())).thenReturn(new StudentResponse(1, "Ivan", "Ivanov"));
        when(students.update(eq(1L), any())).thenReturn(new StudentResponse(1, "Ivan", "Ivanov"));
        mvc.perform(post("/api/v1/student/save").contentType("application/json")
                .content("{\"name\":\"Ivan\",\"surname\":\"Ivanov\",\"phone_number\":\"123\",\"email\":\"i@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        verify(students).save(argThat(request -> "123".equals(request.getPhoneNumber()) && "Ivan".equals(request.getName())));
        mvc.perform(put("/api/v1/student/1").contentType("application/json")
                .content("{\"phoneNumber\":\"456\",\"email\":\"new@example.com\"}")).andExpect(status().isOk());
        verify(students).update(eq(1L), argThat(request -> "456".equals(request.getPhoneNumber())));
        mvc.perform(delete("/api/v1/student/1")).andExpect(status().isOk());
        verify(students).deleteById(1L);
        mvc.perform(get("/api/v1/users")).andExpect(status().isForbidden());
    }
    @Test @WithMockUser(roles = "ADMIN")
    void missingStudentProducesNotFoundJson() throws Exception {
        when(students.findById(99L)).thenThrow(new EntityNotFoundException("Student not found"));
        mvc.perform(get("/api/v1/student/99")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.message").value("Student not found"));
    }
    @Test @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminManagesUsersButDoesNotInheritStudentPermissions() throws Exception {
        var user = new UserResponse(2, "teacher@example.com", Set.of(Role.TEACHER));
        when(users.findAll()).thenReturn(new UserResponseList(List.of(user), 1));
        when(users.save(any())).thenReturn(user);
        mvc.perform(get("/api/v1/users")).andExpect(status().isOk())
                .andExpect(jsonPath("$.responses[0].email").value("teacher@example.com"));
        mvc.perform(post("/api/v1/users/save").contentType("application/json")
                .content("{\"email\":\"teacher@example.com\",\"password\":\"secret\",\"roles\":[\"TEACHER\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2));
        verify(users).save(argThat(request -> request.getRoles().equals(Set.of(Role.TEACHER))));
        mvc.perform(delete("/api/v1/users/2")).andExpect(status().isOk());
        verify(users).deleteById(2L);
        mvc.perform(get("/api/v1/student")).andExpect(status().isForbidden());
        verifyNoInteractions(students);
    }
}
