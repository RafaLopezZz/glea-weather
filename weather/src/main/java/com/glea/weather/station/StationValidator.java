package com.glea.weather.station;

import java.util.Locale;

public class StationValidator {

    public void validate(String provider, String indicativo) {

        if (provider == null) {
            throw new IllegalArgumentException("Provider must not be null.");
        }

        if (indicativo == null) {
            throw new IllegalArgumentException("Indicativo must not be null.");
        }

        if (provider.isBlank()) {
            throw new IllegalArgumentException("Provider must not be blank.");
        }

        if (indicativo.isBlank()) {
            throw new IllegalArgumentException("Indicativo must not be blank.");
        }

        if (!provider.equals(provider.strip())) {
            throw new IllegalArgumentException("Provider must not have leading or trailing whitespace.");
        }

        if (!indicativo.equals(indicativo.strip())) {
            throw new IllegalArgumentException("Indicativo must not have leading or trailing whitespace.");
        }

        if (!provider.equals(provider.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Provider must be uppercase.");
        }

        if (provider.codePointCount(0, provider.length()) > 30) {
            throw new IllegalArgumentException("Provider must not exceed 30 characters.");
        }

        if (indicativo.codePointCount(0, indicativo.length()) > 20) {
            throw new IllegalArgumentException("Indicativo must not exceed 20 characters.");
        }
    }

}
