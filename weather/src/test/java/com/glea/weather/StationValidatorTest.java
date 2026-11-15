package com.glea.weather;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class StationValidatorTest {

    StationValidator validator = new StationValidator();

    @Test
    void shouldFailWhenProviderIsNull() {

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(null, "7211B"));

        assertEquals("Provider must not be null.", error.getMessage());

    }

    @Test
    void shouldFailWhenIndicativoIsNull() {

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AEMET", null));

        assertEquals("Indicativo must not be null.", error.getMessage());

    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "   ", "\t", "\n", "\u2003" })
    void shouldFailWhenProviderIsBlank(String provider) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(provider, "7211B"));

        assertEquals("Provider must not be blank.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "   ", "\t", "\n", "\u2003" })
    void shouldFailWhenIndicativoIsBlank(String indicativo) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AEMET", indicativo));

        assertEquals("Indicativo must not be blank.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { " AEMET", "AEMET ", " AEMET ", "\tAEMET", "AEMET\n", "\u2003AEMET" })
    void shouldFailWhenProviderHasOuterWhitespace(String provider) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(provider, "7211B"));

        assertEquals("Provider must not have leading or trailing whitespace.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { " 7211B", "7211B ", " 7211B ", "\t7211B", "7211B\n", "\u20037211B" })
    void shouldFailWhenIndicativoHasOuterWhitespace(String indicativo) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AEMET", indicativo));

        assertEquals("Indicativo must not have leading or trailing whitespace.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "aemet", "Aemet", "AeMeT" })
    void shouldFailWhenProviderIsNotUppercase(String provider) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(provider, "7211B"));

        assertEquals("Provider must be uppercase.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "7211B", "g2-lowercase", "G2-MiXeD", "0012b", "0", "A B" })
    void shouldAcceptIndicativoExactlyAsSupplied(String indicativo) {
        assertDoesNotThrow(() -> validator.validate("AEMET", indicativo));
    }

    @Test
    void shouldAcceptProviderAndIndicativoAtLengthLimits() {
        assertDoesNotThrow(() -> validator.validate("A".repeat(30), "b".repeat(20)));
    }

    @Test
    void shouldAcceptSingleCharacterIdentifiersAndInternalSpaces() {
        assertDoesNotThrow(() -> validator.validate("A", "0"));
        assertDoesNotThrow(() -> validator.validate("OTHER SOURCE", "0012b"));
    }

    @Test
    void shouldCountUnicodeCharactersRatherThanUtf16Units() {
        assertDoesNotThrow(() -> validator.validate("\uD801\uDC00".repeat(30), "\uD83D\uDE80".repeat(20)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "A", "\uD801\uDC00" })
    void shouldFailWhenProviderExceedsLengthLimit(String character) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(character.repeat(31), "7211B"));

        assertEquals("Provider must not exceed 30 characters.", error.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = { "b", "\uD83D\uDE80" })
    void shouldFailWhenIndicativoExceedsLengthLimit(String character) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate("AEMET", character.repeat(21)));

        assertEquals("Indicativo must not exceed 20 characters.", error.getMessage());
    }
}
