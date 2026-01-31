package io.github.driftwood.rules;

import io.github.driftwood.compare.ArrayOrder;
import io.github.driftwood.model.NodePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleSetTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesRuleFileDirectives() throws Exception {
        Path rulesFile = tempDir.resolve("rules.txt");
        Files.writeString(
                rulesFile,
                "# project rules\n"
                        + "ignore cache.**\n"
                        + "mask *.password \"<hidden>\"\n"
                        + "unordered inventory.items\n",
                StandardCharsets.UTF_8);

        RuleSet rules = RuleSet.load(rulesFile, RuleSet.defaults(false, "<masked>"));

        assertTrue(rules.shouldIgnore(NodePath.parse("cache.entry")));
        assertTrue(rules.shouldMask(NodePath.parse("service.password")));
        assertEquals(
                ArrayOrder.UNORDERED,
                rules.arrayOrder(NodePath.parse("inventory.items"), ArrayOrder.ORDERED));
        assertFalse(rules.shouldMask(NodePath.parse("service.name")));
    }

    @Test
    void laterArrayRulesTakePrecedence() throws Exception {
        RuleSet rules = RuleSet.defaults(false, "<masked>");
        rules.add(Rule.arrayOrder(NodePath.parse("items"), ArrayOrder.UNORDERED));
        rules.add(Rule.arrayOrder(NodePath.parse("items"), ArrayOrder.ORDERED));

        assertEquals(ArrayOrder.ORDERED, rules.arrayOrder(NodePath.parse("items"), ArrayOrder.UNORDERED));
    }

    @Test
    void reportsRuleLineForInvalidDirective() throws Exception {
        Path rulesFile = tempDir.resolve("bad.rules");
        Files.writeString(rulesFile, "array-order items sideways\n", StandardCharsets.UTF_8);

        io.github.driftwood.core.ConfigException error = assertThrows(
                io.github.driftwood.core.ConfigException.class,
                () -> RuleSet.load(rulesFile, new RuleSet()));

        assertTrue(error.getMessage().contains("line 1"));
        assertTrue(error.getMessage().contains("ordered or unordered"));
    }

    @Test
    void builtInSensitiveRuleMasksTokenLikeFields() {
        RuleSet rules = RuleSet.defaults(true, "<masked>");

        assertTrue(rules.shouldMask(NodePath.parse("service.apiToken")));
        assertEquals("<masked>", rules.maskValue(
                NodePath.parse("service.apiToken"),
                io.github.driftwood.model.ConfigNode.string("value")));
    }
}
