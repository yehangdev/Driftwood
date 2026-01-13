package io.github.driftwood.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public final class ConfigLoader {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public Map<String, String> load(Path path, ConfigFormat requestedFormat) throws ConfigException {
        if (!Files.isRegularFile(path)) {
            throw new ConfigException("configuration file does not exist: " + path);
        }

        ConfigFormat format = requestedFormat == ConfigFormat.AUTO
                ? ConfigFormat.detect(path)
                : requestedFormat;

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            if (format == ConfigFormat.PROPERTIES) {
                return loadProperties(reader);
            }
            if (format == ConfigFormat.ENV) {
                return loadEnvironment(reader);
            }
            return loadStructured(reader, format, path);
        } catch (IOException e) {
            throw new ConfigException("failed to read '" + path + "': " + e.getMessage(), e);
        }
    }

    private Map<String, String> loadProperties(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);

        Map<String, String> values = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            values.put(key, properties.getProperty(key));
        }
        return values;
    }

    private Map<String, String> loadEnvironment(Reader reader) throws IOException {
        Map<String, String> values = new TreeMap<>();
        java.io.BufferedReader buffered = new java.io.BufferedReader(reader);
        String line;
        int lineNumber = 0;
        while ((line = buffered.readLine()) != null) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("export ")) {
                trimmed = trimmed.substring("export ".length()).trim();
            }
            int equals = trimmed.indexOf('=');
            if (equals <= 0) {
                throw new IOException("invalid environment entry on line " + lineNumber);
            }
            String key = trimmed.substring(0, equals).trim();
            String value = trimmed.substring(equals + 1).trim();
            values.put(key, unquoteEnvironmentValue(value));
        }
        return values;
    }

    private String unquoteEnvironmentValue(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        int comment = value.indexOf(" #");
        return comment < 0 ? value : value.substring(0, comment).trim();
    }

    private Map<String, String> loadStructured(
            Reader reader,
            ConfigFormat format,
            Path path) throws IOException, ConfigException {
        ObjectMapper mapper = format == ConfigFormat.YAML ? yamlMapper : jsonMapper;
        JsonNode root = mapper.readTree(reader);
        if (root == null || root.isNull()) {
            return Collections.emptyMap();
        }
        if (!root.isObject()) {
            throw new ConfigException("root value in '" + path + "' must be an object");
        }

        Map<String, String> values = new TreeMap<>();
        flattenObject("", root, values);
        return values;
    }

    private void flattenObject(String prefix, JsonNode object, Map<String, String> values) {
        Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
            flattenValue(key, field.getValue(), values);
        }
    }

    private void flattenValue(String key, JsonNode value, Map<String, String> values) {
        if (value.isObject()) {
            flattenObject(key, value, values);
        } else if (value.isNull()) {
            values.put(key, null);
        } else if (value.isArray()) {
            values.put(key, value.toString());
        } else {
            values.put(key, value.asText());
        }
    }
}
