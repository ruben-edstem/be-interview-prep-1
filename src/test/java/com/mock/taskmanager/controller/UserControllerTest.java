package com.mock.taskmanager.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mock.taskmanager.config.SecurityConfig;
import com.mock.taskmanager.dto.response.UserResponse;
import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER_USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void meWithoutATokenReturns401AsJson() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    void listWithoutATokenReturns401AsJson() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(userService);
    }

    @Test
    void anInvalidTokenReturns401AsJson() throws Exception {
        when(jwtDecoder.decode("garbage")).thenThrow(new BadJwtException("bad token"));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Authentication is required to access this resource"));
    }

    @Test
    void meReturnsTheProfileOfTheUserInTheToken() throws Exception {
        when(userService.getProfile(USER_ID))
                .thenReturn(new UserResponse(USER_ID, "ada@example.com", Role.USER, Instant.now()));

        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.email").value("ada@example.com"));
    }

    @Test
    void meIgnoresAnIdSentInTheRequest() throws Exception {
        when(userService.getProfile(USER_ID))
                .thenReturn(new UserResponse(USER_ID, "ada@example.com", Role.USER, Instant.now()));

        mockMvc.perform(get("/api/v1/users/me").param("id", OTHER_USER_ID.toString())
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()));

        verify(userService).getProfile(USER_ID);
    }

    @Test
    void aUserCannotListAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(userService);
    }

    @Test
    void aUserCannotReachAnythingUnderTheAdminUsersPath() throws Exception {
        mockMvc.perform(get("/api/v1/users/" + OTHER_USER_ID)
                        .with(jwt().jwt(token -> token.subject(USER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anAdminCanListAllUsers() throws Exception {
        Pageable pageable = PageRequest.of(0, 20);
        UserResponse ada = new UserResponse(USER_ID, "ada@example.com", Role.USER, Instant.now());
        when(userService.list(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(ada), pageable, 1));

        mockMvc.perform(get("/api/v1/users")
                        .with(jwt().jwt(token -> token.subject(OTHER_USER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].email").value("ada@example.com"))
                .andExpect(jsonPath("$.data.page.totalElements").value(1));
    }

    @Test
    void anAdminCanStillViewTheirOwnProfile() throws Exception {
        when(userService.getProfile(OTHER_USER_ID))
                .thenReturn(new UserResponse(OTHER_USER_ID, "root@example.com", Role.ADMIN, Instant.now()));

        mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(token -> token.subject(OTHER_USER_ID.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }
}
