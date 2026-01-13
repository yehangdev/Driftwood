package io.github.driftwood.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private final ConfigLoader loader = new ConfigLoader();

    @Test
    void loadsPropertiesFile() throws Exception {
        Path file = write("application.properties", "app.name=demo\nserver.port=8080\n");

        Map<String, String> values = loader.load(file, ConfigFormat.AUTO);

        assertEquals("demo", values.get("app.name"));
        assertEquals("8080", values.get("server.port"));
    }

    @Test
    void flattensYamlObjectsAndKeepsArrays() throws Exception {
        Path file = write("application.yml",
                "server:\n"
                        + "  port: 8080\n"
                        + "  hosts:\n"
                        + "    - alpha\n"
                        + "    - beta\n"
                        + "feature:\n"
                        + "  enabled: true\n"
                        + "service:\n"
                        + "  password: top-secret\n");

        Map<String, String> values = loader.load(file, ConfigFormat.AUTO);

        assertEquals("8080", values.get("server.port"));
        assertEquals("[\"alpha\",\"beta\"]", values.get("server.hosts"));
        assertEquals("true", values.get("feature.enabled"));
        assertEquals("top-secret", values.get("service.password"));
    }

    @Test
    void flattensJsonFile() throws Exception {
        Path file = write("application.json",
                "{\"server\":{\"port\":9090},\"feature\":{\"enabled\":false}}");

        Map<String, String> values = loader.load(file, ConfigFormat.AUTO);

        assertEquals("9090", values.get("server.port"));
        assertEquals("false", values.get("feature.enabled"));
    }

    @Test
    void rejectsUnknownFormatWhenItCannotBeDetected() throws Exception {
        Path file = write("application.conf", "server.port=8080\n");

        ConfigException error = assertThrows(
                ConfigException.class,
                () -> loader.load(file, ConfigFormat.AUTO));

        assertEquals(true, error.getMessage().contains("specify the format explicitly"));
    }

    @Test
    void rejectsNonObjectRoot() throws Exception {
        Path file = write("application.json", "[\"one\",\"two\"]");

        ConfigException error = assertThrows(
                ConfigException.class,
                () -> loader.load(file, ConfigFormat.JSON));

        assertEquals(true, error.getMessage().contains("must be an object"));
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
