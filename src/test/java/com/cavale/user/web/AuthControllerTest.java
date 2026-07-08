package com.cavale.user.web;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.cavale.common.config.SecurityConfig;
import com.cavale.user.domain.User;
import com.cavale.user.service.EmailAlreadyUsedException;
import com.cavale.user.service.UserService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static User userWithId(UUID id) {
        User user = new User("alice@cavale.run", "$2a$hashed", "Alice");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void register_returns201WithLocationAndBody() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.register(anyString(), anyString(), anyString())).thenReturn(userWithId(id));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "alice@cavale.run", "password": "s3cret-pass", "displayName": "Alice"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/users/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.email").value("alice@cavale.run"))
                .andExpect(jsonPath("$.displayName").value("Alice"));
    }

    @Test
    void register_returns400OnInvalidBody() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short", "displayName": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.displayName").exists());
    }

    @Test
    void register_returns409OnDuplicateEmail() throws Exception {
        when(userService.register(anyString(), anyString(), anyString()))
                .thenThrow(new EmailAlreadyUsedException("alice@cavale.run"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "alice@cavale.run", "password": "s3cret-pass", "displayName": "Alice"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Email already in use"));
    }
}
