package com.mock.taskmanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.mock.taskmanager.config.JwtConfig;
import com.mock.taskmanager.config.JwtProperties;
import com.mock.taskmanager.dto.response.TokenResponse;
import com.mock.taskmanager.entity.Role;
import com.mock.taskmanager.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class TokenServiceTest {

    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SECRET = "unit-test-signing-key-with-more-than-32-chars";

    private JwtProperties properties;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(SECRET, Duration.ofMinutes(15));
        decoder = new JwtConfig().jwtDecoder(properties);
    }

    @Test
    void issuedTokenCarriesTheUserIdAndRoleAndExpiresAfterFifteenMinutes() {
        Instant now = Instant.now();
        TokenService tokenService = tokenServiceAt(now);

        TokenResponse token = tokenService.issue(user(Role.ADMIN));

        Jwt jwt = decoder.decode(token.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.<String>getClaim("role")).isEqualTo("ADMIN");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
        assertThat(token.tokenType()).isEqualTo("Bearer");
        assertThat(token.expiresIn()).isEqualTo(900);
    }

    @Test
    void tokenIssuedMoreThanFifteenMinutesAgoIsRejectedByTheDecoder() {
        TokenService tokenService = tokenServiceAt(Instant.now().minus(Duration.ofMinutes(15)).minusSeconds(1));

        TokenResponse token = tokenService.issue(user(Role.USER));

        assertThrows(JwtException.class, () -> decoder.decode(token.accessToken()));
    }

    private TokenService tokenServiceAt(Instant instant) {
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        return new TokenService(new JwtConfig().jwtEncoder(properties), properties, clock);
    }

    private User user(Role role) {
        return User.builder().id(USER_ID).email("ada@example.com").passwordHash("hashed-pass").role(role).build();
    }
}
