package com.mock.taskmanager.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock.taskmanager.config.SecurityConfig;
import com.mock.taskmanager.dto.request.LoginRequest;
import com.mock.taskmanager.dto.request.RegisterRequest;
import com.mock.taskmanager.dto.response.TokenResponse;
import com.mock.taskmanager.dto.response.UserResponse;
import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.exception.EmailAlreadyRegisteredException;
import com.mock.taskmanager.exception.InvalidCredentialsException;
import com.mock.taskmanager.service.AuthService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void registerWithoutATokenReturns201WithTheNewUserAndNoPassword() throws Exception {
        UserResponse created = new UserResponse(USER_ID, "ada@example.com", Role.USER, Instant.now());
        when(authService.register(any(RegisterRequest.class))).thenReturn(created);
        String body = """
                {"email": "ada@example.com", "password": "s3cret-pass"}
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void registerIgnoresARoleSentByTheClient() throws Exception {
        UserResponse created = new UserResponse(USER_ID, "ada@example.com", Role.USER, Instant.now());
        when(authService.register(any(RegisterRequest.class))).thenReturn(created);
        String body = """
                {"email": "ada@example.com", "password": "s3cret-pass", "role": "ADMIN"}
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void registerWithInvalidFieldsReportsEachOne() throws Exception {
        String body = """
                {"email": "not-an-email", "password": "short"}
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));

        verifyNoInteractions(authService);
    }

    @Test
    void registerWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }

    @Test
    void registerWithPasswordOver72BytesReturns400() throws Exception {
        String body = """
                {"email": "ada@example.com", "password": "%s"}
                """.formatted("a".repeat(73));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("password must be at most 72 bytes"));
    }

    @Test
    void registerWithFewerThan72CharactersButMoreThan72BytesReturns400() throws Exception {
        String body = """
                {"email": "ada@example.com", "password": "%s"}
                """.formatted("é".repeat(40));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("password must be at most 72 bytes"));

        verifyNoInteractions(authService);
    }

    @Test
    void registerWithAPasswordOfExactly72BytesIsAccepted() throws Exception {
        UserResponse created = new UserResponse(USER_ID, "ada@example.com", Role.USER, Instant.now());
        when(authService.register(any(RegisterRequest.class))).thenReturn(created);
        String body = """
                {"email": "ada@example.com", "password": "%s"}
                """.formatted("é".repeat(36));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void registerWithAPasswordUnder8CharactersReturns400() throws Exception {
        String body = """
                {"email": "ada@example.com", "password": "short"}
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("password must be at least 8 characters"));
    }

    @Test
    void registerWithATakenEmailReturns409() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenThrow(new EmailAlreadyRegisteredException());
        String body = """
                {"email": "ada@example.com", "password": "s3cret-pass"}
                """;

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void loginWithoutATokenReturnsABearerToken() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(TokenResponse.bearer("signed-token", 900));
        String body = """
                {"email": "ada@example.com", "password": "s3cret-pass"}
                """;

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void loginWithBadCredentialsReturns401InTheCommonErrorFormat() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());
        String body = """
                {"email": "ada@example.com", "password": "wrong-pass"}
                """;

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginWithMissingFieldsReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(2));
    }
}
