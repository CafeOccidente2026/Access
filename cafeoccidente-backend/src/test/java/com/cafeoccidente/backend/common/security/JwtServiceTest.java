package com.cafeoccidente.backend.common.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** S6: con un JWT_SECRET de menos de 32 caracteres el backend no arranca, y dice por que. */
class JwtServiceTest {

    @Test
    void aShortSecretStopsTheStart() {
        assertThatThrownBy(() -> new JwtService("x".repeat(31), 1000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(JwtService.SHORT_SECRET);
        assertThatThrownBy(() -> new JwtService(null, 1000)).hasMessage(JwtService.SHORT_SECRET);
    }

    @Test
    void thirtyTwoCharactersAreEnough() {
        assertThatCode(() -> new JwtService("x".repeat(32), 1000)).doesNotThrowAnyException();
    }
}
