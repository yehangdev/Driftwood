package io.github.driftwood.compare;

public enum ArrayOrder {
    ORDERED,
    UNORDERED;

    public static ArrayOrder fromName(String value) {
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("array order must be ordered or unordered: " + value);
        }
    }
}
