package io.github.driftwood.schema;

import io.github.driftwood.model.ConfigDocument;
import io.github.driftwood.model.ConfigNode;
import io.github.driftwood.core.ConfigFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void validatesRequiredPathsAndScalarTypes() throws Exception {
        Path schemaFile = tempDir.resolve("schema.yml");
        Files.writeString(
                schemaFile,
                "required:\n"
                        + "  - server.port\n"
                        + "types:\n"
                        + "  server.port: integer\n"
                        + "  feature.enabled: boolean\n"
                        + "arrays:\n"
                        + "  server.hosts: unordered\n",
                StandardCharsets.UTF_8);
        SchemaDefinition schema = SchemaDefinition.load(schemaFile);
        ConfigNode root = object(
                "server", object(
                        "port", ConfigNode.string("8080")),
                "feature", object(
                        "enabled", ConfigNode.bool(true)));
        ConfigDocument document = new ConfigDocument(
                tempDir.resolve("application.yml"),
                ConfigFormat.YAML,
                root);

        List<String> issues = new SchemaValidator().validate(document, schema);

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).contains("server.port"));
        assertTrue(issues.get(0).contains("integer"));
    }

    @Test
    void acceptsNumbersAndRejectsUnknownSchemaSections() throws Exception {
        SchemaDefinition schema = SchemaDefinition.parse(
                object("types", object("port", ConfigNode.string("number"))),
                tempDir.resolve("schema.yml"));
        ConfigDocument document = new ConfigDocument(
                tempDir.resolve("application.yml"),
                ConfigFormat.YAML,
                object("port", ConfigNode.number("8080")));

        assertTrue(new SchemaValidator().validate(document, schema).isEmpty());
    }

    @Test
    void rejectsUnknownSchemaSection() {
        io.github.driftwood.core.ConfigException error = org.junit.jupiter.api.Assertions.assertThrows(
                io.github.driftwood.core.ConfigException.class,
                () -> SchemaDefinition.parse(
                        object("unknown", ConfigNode.string("value")),
                        tempDir.resolve("schema.yml")));

        assertTrue(error.getMessage().contains("unknown schema section"));
    }

    private ConfigNode object(Object... pairs) {
        Map<String, ConfigNode> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put((String) pairs[index], (ConfigNode) pairs[index + 1]);
        }
        return ConfigNode.object(values);
    }
}
