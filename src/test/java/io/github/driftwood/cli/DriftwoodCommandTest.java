package io.github.driftwood.cli;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriftwoodCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsOneAndPrintsDifferencesWithRedaction() throws Exception {
        Path left = write("left.yml",
                "server:\n"
                        + "  port: 8080\n"
                        + "feature:\n"
                        + "  old: true\n"
                        + "service:\n"
                        + "  token: left-token-value\n");
        Path right = write("right.json",
                "{\"server\":{\"port\":9090},\"feature\":{\"new\":true},"
                        + "\"service\":{\"token\":\"right-token-value\"}}");

        CommandResult result = run(
                "--left", left.toString(),
                "--right", right.toString());

        assertEquals(1, result.exitCode);
        assertTrue(result.out.contains("Added keys (1):"));
        assertTrue(result.out.contains("  + feature.new"));
        assertTrue(result.out.contains("Removed keys (1):"));
        assertTrue(result.out.contains("  - feature.old"));
        assertTrue(result.out.contains("Changed keys (2):"));
        assertTrue(result.out.contains("~ server.port: \"8080\" -> \"9090\""));
        assertTrue(result.out.contains("~ service.token: <redacted> -> <redacted>"));
        assertFalse(result.out.contains("left-token-value"));
        assertFalse(result.out.contains("right-token-value"));
    }

    @Test
    void returnsZeroWhenInputsAreEqual() throws Exception {
        Path left = write("left.properties", "app.name=demo\nserver.port=8080\n");
        Path right = write("right.properties", "server.port=8080\napp.name=demo\n");

        CommandResult result = run(
                "--left", left.toString(),
                "--right", right.toString());

        assertEquals(0, result.exitCode);
        assertTrue(result.out.contains("No configuration differences."));
    }

    @Test
    void canDisableRedaction() throws Exception {
        Path left = write("left.properties", "service.secret=left-secret\n");
        Path right = write("right.properties", "service.secret=right-secret\n");

        CommandResult result = run(
                "--left", left.toString(),
                "--right", right.toString(),
                "--no-redact");

        assertEquals(1, result.exitCode);
        assertTrue(result.out.contains("left-secret"));
        assertTrue(result.out.contains("right-secret"));
    }

    @Test
    void acceptsExplicitFormatsForUnknownExtensions() throws Exception {
        Path left = write("left.conf", "app.name=demo\n");
        Path right = write("right.conf", "app.name=demo\n");

        CommandResult result = run(
                "--left", left.toString(),
                "--left-format", "PROPERTIES",
                "--right", right.toString(),
                "--right-format", "PROPERTIES");

        assertEquals(0, result.exitCode);
        assertTrue(result.out.contains("No configuration differences."));
    }

    @Test
    void returnsTwoForUnreadableInput() {
        CommandResult result = run(
                "--left", tempDir.resolve("missing.properties").toString(),
                "--right", tempDir.resolve("missing.properties").toString());

        assertEquals(2, result.exitCode);
        assertTrue(result.err.contains("configuration file does not exist"));
    }

    private CommandResult run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        CommandLine commandLine = new CommandLine(new DriftwoodCommand());
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));

        int exitCode = commandLine.execute(args);
        return new CommandResult(exitCode, out.toString(), err.toString());
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static final class CommandResult {

        private final int exitCode;
        private final String out;
        private final String err;

        private CommandResult(int exitCode, String out, String err) {
            this.exitCode = exitCode;
            this.out = out;
            this.err = err;
        }
    }
}
