package io.github.driftwood.model;

import java.util.Locale;

public enum ScalarType {
    STRING,
    NUMBER,
    BOOLEAN;

    public static ScalarType fromName(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "string":
                return STRING;
            case "number":
            case "integer":
                return NUMBER;
            case "boolean":
                return BOOLEAN;
            default:
                throw new IllegalArgumentException("unsupported scalar type: " + value);
        }
    }
}
