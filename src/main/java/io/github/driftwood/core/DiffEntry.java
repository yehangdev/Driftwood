package io.github.driftwood.core;

import java.util.Objects;

public final class DiffEntry {

    public enum Type {
        ADDED,
        REMOVED,
        CHANGED
    }

    private final Type type;
    private final String key;
    private final String leftValue;
    private final String rightValue;

    public DiffEntry(Type type, String key, String leftValue, String rightValue) {
        this.type = Objects.requireNonNull(type, "type");
        this.key = Objects.requireNonNull(key, "key");
        this.leftValue = leftValue;
        this.rightValue = rightValue;
    }

    public Type getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public String getLeftValue() {
        return leftValue;
    }

    public String getRightValue() {
        return rightValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DiffEntry)) {
            return false;
        }
        DiffEntry that = (DiffEntry) other;
        return type == that.type
                && key.equals(that.key)
                && Objects.equals(leftValue, that.leftValue)
                && Objects.equals(rightValue, that.rightValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, key, leftValue, rightValue);
    }

    @Override
    public String toString() {
        return "DiffEntry{"
                + "type=" + type
                + ", key='" + key + '\''
                + ", leftValue='" + leftValue + '\''
                + ", rightValue='" + rightValue + '\''
                + '}';
    }
}
