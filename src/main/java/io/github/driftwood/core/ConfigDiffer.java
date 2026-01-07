package io.github.driftwood.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class ConfigDiffer {

    public static final String REDACTED = "<redacted>";

    private static final Pattern SENSITIVE_KEY_PATTERN =
            Pattern.compile("(?i).*(password|secret|token).*");

    public List<DiffEntry> compare(Map<String, String> left, Map<String, String> right) {
        return compare(left, right, ConfigDiffer::isSensitiveKey);
    }

    public List<DiffEntry> compare(
            Map<String, String> left,
            Map<String, String> right,
            Predicate<String> shouldRedact) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(shouldRedact, "shouldRedact");

        Set<String> keys = new TreeSet<>();
        keys.addAll(left.keySet());
        keys.addAll(right.keySet());

        List<DiffEntry> differences = new ArrayList<>();
        for (String key : keys) {
            boolean inLeft = left.containsKey(key);
            boolean inRight = right.containsKey(key);

            if (!inLeft) {
                differences.add(new DiffEntry(
                        DiffEntry.Type.ADDED,
                        key,
                        null,
                        displayValue(key, right.get(key), shouldRedact)));
            } else if (!inRight) {
                differences.add(new DiffEntry(
                        DiffEntry.Type.REMOVED,
                        key,
                        displayValue(key, left.get(key), shouldRedact),
                        null));
            } else if (!Objects.equals(left.get(key), right.get(key))) {
                differences.add(new DiffEntry(
                        DiffEntry.Type.CHANGED,
                        key,
                        displayValue(key, left.get(key), shouldRedact),
                        displayValue(key, right.get(key), shouldRedact)));
            }
        }
        return Collections.unmodifiableList(differences);
    }

    public static boolean isSensitiveKey(String key) {
        return SENSITIVE_KEY_PATTERN.matcher(key).matches();
    }

    private String displayValue(
            String key,
            String value,
            Predicate<String> shouldRedact) {
        if (shouldRedact.test(key)) {
            return REDACTED;
        }
        if (value == null) {
            return "null";
        }
        return '"' + escape(value) + '"';
    }

    private String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
