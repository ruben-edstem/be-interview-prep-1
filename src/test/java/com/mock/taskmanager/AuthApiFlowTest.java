package com.mock.taskmanager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.service.UserService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthApiFlowTest {

    private static final String PASSWORD = "s3cret-pass";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void registeredUserCanLogInAndViewTheirOwnProfile() throws Exception {
        String email = uniqueEmail();
        register(email);

        String token = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void aUserCannotAccessTheAdminEndpoint() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void anAdminCanListAllUsersWithoutExposingPasswordHashes() throws Exception {
        String adminEmail = uniqueEmail();
        userService.create(adminEmail, PASSWORD, Role.ADMIN);
        register(uniqueEmail());
        String token = login(adminEmail, PASSWORD);

        String body = mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains(adminEmail).doesNotContain("password");
    }

    @Test
    void anAdminCannotSortTheUserListByThePasswordHash() throws Exception {
        String adminEmail = uniqueEmail();
        userService.create(adminEmail, PASSWORD, Role.ADMIN);
        String token = login(adminEmail, PASSWORD);

        mockMvc.perform(get("/api/v1/users").param("sort", "passwordHash").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"));
    }

    @Test
    void registeringTheSameEmailTwiceReturns409() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void loginWithAWrongPasswordAndLoginWithAnUnknownEmailAreIndistinguishable() throws Exception {
        String email = uniqueEmail();
        register(email);

        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, "not-the-password")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(uniqueEmail(), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.code")).isEqualTo("INVALID_CREDENTIALS");
        assertThat(JsonPath.<String>read(unknownEmail, "$.message"))
                .isEqualTo(JsonPath.<String>read(wrongPassword, "$.message"));
    }

    @Test
    void aRequestWithoutATokenReturns401AsJson() throws Exception {
        mockMvc.perform(get("/api/v1/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void shorteningAndStatsNeedATokenButFollowingAShortLinkDoesNot() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email, PASSWORD);
        String body = """
                {"url": "https://example.com/some/long/path"}
                """;
        String created = mockMvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON)
                        .content(body).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(created, "$.data.code");

        mockMvc.perform(post("/api/v1/urls").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/urls/{code}/stats", code))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/{code}", code))
                .andExpect(status().isFound());
    }

    @Test
    void aValidTokenOpensTheTaskApi() throws Exception {
        String email = uniqueEmail();
        register(email);
        String token = login(email, PASSWORD);

        mockMvc.perform(get("/api/v1/tasks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void anExpiredTokenReturns401() throws Exception {
        String email = uniqueEmail();
        UUID userId = register(email);
        Instant issuedAt = Instant.now().minusSeconds(16 * 60);
        String expired = sign(jwtEncoder, userId, Role.USER, issuedAt, issuedAt.plusSeconds(15 * 60));

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void aTokenSignedWithAnotherKeyReturns401() throws Exception {
        String email = uniqueEmail();
        UUID userId = register(email);
        SecretKeySpec otherKey = new SecretKeySpec(
                "a-completely-different-signing-key-1234".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(otherKey));
        Instant now = Instant.now();
        String forged = sign(otherEncoder, userId, Role.ADMIN, now, now.plusSeconds(600));

        mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void loginTokenExpiresInFifteenMinutes() throws Exception {
        String email = uniqueEmail();
        register(email);

        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<Integer>read(body, "$.data.expiresIn")).isEqualTo(900);
    }

    private UUID register(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.data.id"));
    }

    private String login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(credentials(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.accessToken");
    }

    private String sign(JwtEncoder encoder, UUID userId, Role role, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", role.name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
