package io.github.driftwood.integration;

import io.github.driftwood.cli.DriftwoodCommand;
import io.github.driftwood.compare.ArrayOrder;
import io.github.driftwood.compare.ComparisonOptions;
import io.github.driftwood.compare.ComparisonRequest;
import io.github.driftwood.compare.ComparisonResult;
import io.github.driftwood.compare.ConfigComparisonEngine;
import io.github.driftwood.core.ConfigException;
import io.github.driftwood.core.ConfigFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriftwoodIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void envAndPropertiesInputsCanBeComparedWithSelectors() throws Exception {
        Path env = write(".env",
                "SERVER_PORT=8080\n"
                        + "SERVER_HOST=localhost\n"
                        + "FEATURE_ENABLED=true\n");
        Path properties = write("application.properties",
                "SERVER_PORT=9090\n"
                        + "SERVER_HOST=localhost\n"
                        + "FEATURE_ENABLED=true\n");

        ComparisonRequest request = ComparisonRequest.builder()
                .left(env)
                .right(properties)
                .leftFormat(ConfigFormat.ENV)
                .rightFormat(ConfigFormat.PROPERTIES)
                .options(ComparisonOptions.builder().include("SERVER_PORT").build())
                .sensitiveMasking(false)
                .build();

        ComparisonResult result = new ConfigComparisonEngine().compare(request);

        assertEquals(1, result.getChanges().size());
        assertEquals("SERVER_PORT", result.getChanges().get(0).getPath().toString());
    }

    @Test
    void overlaysAndProfilesAreAppliedBeforeComparison() throws Exception {
        Path left = write("left.yml",
                "service:\n"
                        + "  name: orders\n"
                        + "profiles:\n"
                        + "  dev:\n"
                        + "    port: 8080\n");
        Path right = write("right.yml",
                "service:\n"
                        + "  name: orders\n"
                        + "profiles:\n"
                        + "  dev:\n"
                        + "    port: 9090\n");
        Path overlay = write("overlay.yml", "service:\n  region: east\n");
        ComparisonRequest request = ComparisonRequest.builder()
                .left(left)
                .right(right)
                .leftOverlay(overlay)
                .rightOverlay(overlay)
                .profile("dev")
                .sensitiveMasking(false)
                .build();

        ComparisonResult result = new ConfigComparisonEngine().compare(request);

        assertEquals(1, result.getChanges().size());
        assertEquals("port", result.getChanges().get(0).getPath().toString());
    }

    @Test
    void unorderedArrayRuleChangesMatchingBehavior() throws Exception {
        Path left = write("left.json", "{\"items\":[\"alpha\",\"beta\"]}");
        Path right = write("right.json", "{\"items\":[\"beta\",\"alpha\"]}");
        ComparisonOptions options = ComparisonOptions.builder()
                .defaultArrayOrder(ArrayOrder.UNORDERED)
                .build();

        ComparisonResult result = new ConfigComparisonEngine().compare(
                ComparisonRequest.builder()
                        .left(left)
                        .right(right)
                        .options(options)
                        .sensitiveMasking(false)
                        .build());

        assertTrue(result.isEqual());
    }

    @Test
    void schemaValidationFailureIsReportedAsConfigException() throws Exception {
        Path config = write("application.yml", "server:\n  port: 8080\n");
        Path schema = write("schema.yml",
                "required:\n"
                        + "  - server.host\n"
                        + "types:\n"
                        + "  server.port: string\n");
        ComparisonRequest request = ComparisonRequest.builder()
                .left(config)
                .right(config)
                .schemaFile(schema)
                .sensitiveMasking(false)
                .build();

        ConfigException error = assertThrows(
                ConfigException.class,
                () -> new ConfigComparisonEngine().validate(request));

        assertTrue(error.getMessage().contains("validation failed"));
        assertTrue(error.getMessage().contains("server.host"));
        assertTrue(error.getMessage().contains("server.port"));
    }

    @Test
    void commandWritesJsonReportToFileAndReturnsOne() throws Exception {
        Path left = write("left.properties", "server.port=8080\n");
        Path right = write("right.properties", "server.port=9090\n");
        Path report = tempDir.resolve("report.json");
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = new CommandLine(new DriftwoodCommand());
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));

        int exit = commandLine.execute(
                "--left", left.toString(),
                "--right", right.toString(),
                "--format", "JSON",
                "--output", report.toString(),
                "--context", "2");

        assertEquals(1, exit, err.toString());
        String reportText = Files.readString(report, StandardCharsets.UTF_8);
        assertTrue(reportText.contains("\"path\" : \"server.port\""));
        assertTrue(reportText.contains("\"changed\" : 1"));
        assertEquals("", out.toString());
    }

    @Test
    void invalidRuleFileReturnsUsageExitCode() throws Exception {
        Path left = write("left.properties", "server.port=8080\n");
        Path right = write("right.properties", "server.port=9090\n");
        Path rules = write("rules.txt", "mask\n");
        StringWriter err = new StringWriter();
        CommandLine commandLine = new CommandLine(new DriftwoodCommand());
        commandLine.setOut(new PrintWriter(new StringWriter(), true));
        commandLine.setErr(new PrintWriter(err, true));

        int exit = commandLine.execute(
                "--left", left.toString(),
                "--right", right.toString(),
                "--rules", rules.toString());

        assertEquals(2, exit);
        assertTrue(err.toString().contains("expected an action and a path"));
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
