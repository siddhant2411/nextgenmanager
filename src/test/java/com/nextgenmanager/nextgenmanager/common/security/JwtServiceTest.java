package com.nextgenmanager.nextgenmanager.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    @Test
    void generateAndValidateToken_success() {
        JwtService jwtService = new JwtService(
                "ThisIsASufficientlyLongJwtSecretKeyForTests1234567890_ABCDEFGHIJKLMN",
                60000,
                600000,
                "https://auth.erp.nextgenmanager.com",
                "erp-backend",
                "HS512",
                "test"
        );
        UserDetails userDetails = User.withUsername("admin")
                .password("encoded")
                .authorities("ROLE_ADMIN")
                .build();

        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtService.isTokenValid(token, userDetails)).isTrue();
    }

    @Test
    void constructorRejectsShortSecret() {
        JwtService jwtService = new JwtService(
                "short-secret",
                60000,
                600000,
                "https://auth.erp.nextgenmanager.com",
                "erp-backend",
                "HS512",
                "test"
        );
        UserDetails userDetails = User.withUsername("admin")
                .password("encoded")
                .authorities("ROLE_ADMIN")
                .build();

        assertThatThrownBy(() -> jwtService.generateToken(userDetails))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 64 characters");
    }
}
