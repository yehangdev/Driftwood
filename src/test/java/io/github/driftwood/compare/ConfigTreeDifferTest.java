package io.github.driftwood.compare;

import io.github.driftwood.model.ConfigNode;
import io.github.driftwood.model.NodePath;
import io.github.driftwood.rules.Rule;
import io.github.driftwood.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTreeDifferTest {

    @Test
    void reportsTypedScalarAndObjectChanges() {
        ConfigNode left = object(
                "server", object(
                        "port", ConfigNode.number("8080"),
                        "enabled", ConfigNode.bool(true)),
                "name", ConfigNode.string("service-a"));
        ConfigNode right = object(
                "server", object(
                        "port", ConfigNode.number("9090"),
                        "enabled", ConfigNode.bool(false)),
                "name", ConfigNode.string("service-a"));

        ComparisonResult result = compare(left, right, RuleSet.defaults(false, "<masked>"));

        assertEquals(2, result.getChanges().size());
        assertEquals(ConfigChange.Type.CHANGED, result.getChanges().get(0).getType());
        assertEquals("server.enabled", result.getChanges().get(0).getPath().toString());
        assertEquals("\"false\"", result.getChanges().get(0).getRightDisplay());
        assertEquals("server.port", result.getChanges().get(1).getPath().toString());
    }

    @Test
    void appliesNestedSelectorsAndIgnoreRules() {
        ConfigNode left = object(
                "server", object(
                        "host", ConfigNode.string("left"),
                        "port", ConfigNode.number("8080")),
                "debug", ConfigNode.bool(true));
        ConfigNode right = object(
                "server", object(
                        "host", ConfigNode.string("right"),
                        "port", ConfigNode.number("9090")),
                "debug", ConfigNode.bool(false));
        ComparisonOptions options = ComparisonOptions.builder()
                .include("server")
                .exclude("server.host")
                .build();
        RuleSet rules = RuleSet.defaults(false, "<masked>");
        rules.add(Rule.ignore(NodePath.parse("server.host")));

        ComparisonResult result = new ConfigTreeDiffer(options, rules).compare(left, right);

        assertEquals(1, result.getChanges().size());
        assertEquals("server.port", result.getChanges().get(0).getPath().toString());
        assertEquals(1, result.getIgnoredCount());
    }

    @Test
    void orderedArraysReportIndexedChanges() {
        ConfigNode left = ConfigNode.array(Arrays.asList(
                ConfigNode.string("alpha"),
                ConfigNode.string("beta")));
        ConfigNode right = ConfigNode.array(Arrays.asList(
                ConfigNode.string("alpha"),
                ConfigNode.string("gamma")));

        ComparisonResult result = compare(left, right, RuleSet.defaults(false, "<masked>"));

        assertEquals(1, result.getChanges().size());
        assertEquals("[1]", result.getChanges().get(0).getPath().toString());
        assertEquals(ConfigChange.Type.CHANGED, result.getChanges().get(0).getType());
    }

    @Test
    void unorderedArraysMatchEqualElementsBeforeReportingChanges() {
        ConfigNode left = ConfigNode.array(Arrays.asList(
                ConfigNode.string("alpha"),
                ConfigNode.string("beta"),
                ConfigNode.string("gamma")));
        ConfigNode right = ConfigNode.array(Arrays.asList(
                ConfigNode.string("gamma"),
                ConfigNode.string("alpha"),
                ConfigNode.string("delta")));
        ComparisonOptions options = ComparisonOptions.builder()
                .defaultArrayOrder(ArrayOrder.UNORDERED)
                .build();

        ComparisonResult result = new ConfigTreeDiffer(
                options,
                RuleSet.defaults(false, "<masked>")).compare(left, right);

        assertEquals(2, result.getChanges().size());
        assertEquals("[1]", result.getChanges().get(0).getPath().toString());
        assertEquals(ConfigChange.Type.REMOVED, result.getChanges().get(0).getType());
        assertEquals("[2]", result.getChanges().get(1).getPath().toString());
        assertEquals(ConfigChange.Type.ADDED, result.getChanges().get(1).getType());
    }

    @Test
    void contextContainsNearbyValuesWithoutReplacingChange() {
        ConfigNode left = object(
                "service", object(
                        "name", ConfigNode.string("orders"),
                        "port", ConfigNode.number("8080")),
                "enabled", ConfigNode.bool(true));
        ConfigNode right = object(
                "service", object(
                        "name", ConfigNode.string("orders"),
                        "port", ConfigNode.number("9090")),
                "enabled", ConfigNode.bool(true));
        ComparisonOptions options = ComparisonOptions.builder().contextLines(3).build();

        ComparisonResult result = new ConfigTreeDiffer(
                options,
                RuleSet.defaults(false, "<masked>")).compare(left, right);

        ConfigChange change = result.getChanges().get(0);
        assertTrue(change.getContext().containsKey(NodePath.parse("service.name")));
    }

    private ComparisonResult compare(ConfigNode left, ConfigNode right, RuleSet rules) {
        return new ConfigTreeDiffer(
                ComparisonOptions.builder().build(),
                rules).compare(left, right);
    }

    private ConfigNode object(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("pairs");
        }
        Map<String, ConfigNode> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], (ConfigNode) pairs[index + 1]);
        }
        return ConfigNode.object(values);
    }
}
