package io.github.driftwood.report;

import io.github.driftwood.compare.ComparisonOptions;
import io.github.driftwood.compare.ComparisonResult;
import io.github.driftwood.compare.ConfigChange;
import io.github.driftwood.model.ConfigNode;
import io.github.driftwood.model.NodePath;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportFormatterTest {

    @Test
    void textReportKeepsSectionNamesAndContext() {
        ConfigChange change = new ConfigChange(
                ConfigChange.Type.CHANGED,
                NodePath.parse("server.port"),
                ConfigNode.number("8080"),
                ConfigNode.number("9090"),
                "\"8080\"",
                "\"9090\"",
                Collections.singletonMap(NodePath.parse("server.host"), "\"localhost\""));
        ComparisonResult result = new ComparisonResult(
                ConfigNode.emptyObject(),
                ConfigNode.emptyObject(),
                Collections.singletonList(change),
                0,
                ComparisonOptions.builder().build());
        StringWriter output = new StringWriter();

        new TextReportFormatter().write(result, new PrintWriter(output, true));

        assertTrue(output.toString().contains("Changed keys (1):"));
        assertTrue(output.toString().contains("~ server.port: \"8080\" -> \"9090\""));
        assertTrue(output.toString().contains("context: server.host"));
    }

    @Test
    void jsonReportContainsTypedValuesAndSummary() {
        ConfigChange change = new ConfigChange(
                ConfigChange.Type.ADDED,
                NodePath.parse("feature.enabled"),
                null,
                ConfigNode.bool(true),
                null,
                "\"true\"",
                Collections.emptyMap());
        ComparisonResult result = new ComparisonResult(
                ConfigNode.emptyObject(),
                ConfigNode.emptyObject(),
                Collections.singletonList(change),
                2,
                ComparisonOptions.builder().build());
        StringWriter output = new StringWriter();

        new JsonReportFormatter().write(result, new PrintWriter(output, true));

        assertTrue(output.toString().contains("\"added\" : 1"));
        assertTrue(output.toString().contains("\"right\" : true"));
        assertTrue(output.toString().contains("\"ignored\" : 2"));
    }

    @Test
    void markdownReportRendersTableAndSummary() {
        ConfigChange change = new ConfigChange(
                ConfigChange.Type.REMOVED,
                NodePath.parse("legacy.flag"),
                ConfigNode.bool(false),
                null,
                "\"false\"",
                null,
                Collections.emptyMap());
        ComparisonResult result = new ComparisonResult(
                ConfigNode.emptyObject(),
                ConfigNode.emptyObject(),
                Collections.singletonList(change),
                0,
                ComparisonOptions.builder().build());
        StringWriter output = new StringWriter();

        new MarkdownReportFormatter().write(result, new PrintWriter(output, true));

        assertTrue(output.toString().contains("| Type | Path | Left | Right |"));
        assertTrue(output.toString().contains("| removed | `legacy.flag`"));
        assertTrue(output.toString().contains("Summary: 0 added, 1 removed, 0 changed, 0 ignored."));
    }
}
