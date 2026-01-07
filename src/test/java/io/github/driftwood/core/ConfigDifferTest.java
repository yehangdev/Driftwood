package io.github.driftwood.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigDifferTest {

    private final ConfigDiffer differ = new ConfigDiffer();

    @Test
    void reportsAddedRemovedAndChangedKeysInOrder() {
        Map<String, String> left = Map.of(
                "app.name", "orders",
                "server.port", "8080",
                "feature.old", "true");
        Map<String, String> right = Map.of(
                "app.name", "orders",
                "server.port", "9090",
                "feature.new", "true");

        List<DiffEntry> entries = differ.compare(left, right);

        assertEquals(3, entries.size());
        assertEquals(new DiffEntry(
                DiffEntry.Type.ADDED,
                "feature.new",
                null,
                "\"true\""), entries.get(0));
        assertEquals(new DiffEntry(
                DiffEntry.Type.REMOVED,
                "feature.old",
                "\"true\"",
                null), entries.get(1));
        assertEquals(new DiffEntry(
                DiffEntry.Type.CHANGED,
                "server.port",
                "\"8080\"",
                "\"9090\""), entries.get(2));
    }

    @Test
    void redactsSensitiveValuesButStillReportsChanges() {
        List<DiffEntry> entries = differ.compare(
                Map.of("service.apiToken", "left-token"),
                Map.of("service.apiToken", "right-token"));

        assertEquals(1, entries.size());
        assertEquals(DiffEntry.Type.CHANGED, entries.get(0).getType());
        assertEquals(ConfigDiffer.REDACTED, entries.get(0).getLeftValue());
        assertEquals(ConfigDiffer.REDACTED, entries.get(0).getRightValue());
    }

    @Test
    void distinguishesNullAndNullText() {
        List<DiffEntry> entries = differ.compare(
                Map.of("feature.value", "null"),
                java.util.Collections.singletonMap("feature.value", null));

        assertEquals(1, entries.size());
        assertEquals("\"null\"", entries.get(0).getLeftValue());
        assertEquals("null", entries.get(0).getRightValue());
    }
}
