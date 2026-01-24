package io.github.driftwood.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.driftwood.core.ConfigException;
import io.github.driftwood.core.ConfigFormat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class DocumentLoader {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public ConfigDocument load(Path path, ConfigFormat requestedFormat) throws ConfigException {
        if (path == null) {
            throw new ConfigException("configuration path must not be null");
        }
        if (!Files.isRegularFile(path)) {
            throw new ConfigException("configuration file does not exist: " + path);
        }

        ConfigFormat format = requestedFormat == ConfigFormat.AUTO
                ? ConfigFormat.detect(path)
                : requestedFormat;
        try {
            ConfigNode root;
            if (format == ConfigFormat.PROPERTIES) {
                root = loadProperties(path);
            } else if (format == ConfigFormat.ENV) {
                root = loadEnvironment(path);
            } else {
                root = loadStructured(path, format);
            }
            return new ConfigDocument(path, format, root);
        } catch (IOException e) {
            throw new ConfigException("failed to read '" + path + "': " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ConfigException("invalid '" + path + "': " + e.getMessage(), e);
        }
    }

    private ConfigNode loadProperties(Path path) throws IOException, ConfigException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        ConfigNode root = ConfigNode.emptyObject();
        for (String key : properties.stringPropertyNames()) {
            root = insertPath(root, key, ConfigNode.string(properties.getProperty(key)), path);
        }
        return root;
    }

    private ConfigNode loadEnvironment(Path path) throws IOException, ConfigException {
        ConfigNode root = ConfigNode.emptyObject();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String entry = line.trim();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                if (entry.startsWith("export ")) {
                    entry = entry.substring("export ".length()).trim();
                }
                int equals = entry.indexOf('=');
                if (equals <= 0) {
                    throw new ConfigException(
                            "invalid environment entry in '" + path + "' at line " + lineNumber);
                }
                String key = entry.substring(0, equals).trim();
                validateEnvironmentKey(key, path, lineNumber);
                String value = parseEnvironmentValue(entry.substring(equals + 1).trim());
                root = insertPath(root, key, ConfigNode.string(value), path);
            }
        }
        return root;
    }

    private ConfigNode loadStructured(Path path, ConfigFormat format) throws IOException, ConfigException {
        ObjectMapper mapper = format == ConfigFormat.YAML ? yamlMapper : jsonMapper;
        final JsonNode root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            root = mapper.readTree(reader);
        }
        if (root == null || root.isNull()) {
            return ConfigNode.emptyObject();
        }
        if (!root.isObject()) {
            throw new ConfigException("root value in '" + path + "' must be an object");
        }
        return ConfigNode.fromJson(root);
    }

    private ConfigNode insertPath(
            ConfigNode root,
            String dottedKey,
            ConfigNode value,
            Path source) throws ConfigException {
        String[] parts = dottedKey.split("\\.", -1);
        return insertPath(root, parts, 0, dottedKey, value, source);
    }

    private ConfigNode insertPath(
            ConfigNode current,
            String[] parts,
            int index,
            String dottedKey,
            ConfigNode value,
            Path source) throws ConfigException {
        String part = parts[index];
        if (part.isEmpty()) {
            throw new ConfigException("empty path segment in key '" + dottedKey + "' in " + source);
        }
        Map<String, ConfigNode> values = new LinkedHashMap<>(current.asObject());
        ConfigNode child = values.get(part);
        if (index == parts.length - 1) {
            if (child != null) {
                throw new ConfigException("duplicate key '" + dottedKey + "' in " + source);
            }
            values.put(part, value);
            return ConfigNode.object(values);
        }
        if (child == null) {
            child = ConfigNode.emptyObject();
        } else if (!child.isObject()) {
            throw new ConfigException(
                    "key conflict at '" + part + "' while loading '" + dottedKey + "' in " + source);
        }
        values.put(part, insertPath(child, parts, index + 1, dottedKey, value, source));
        return ConfigNode.object(values);
    }

    private void validateEnvironmentKey(String key, Path source, int lineNumber) throws ConfigException {
        if (!key.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
            throw new ConfigException(
                    "invalid environment key '" + key + "' in " + source + " at line " + lineNumber);
        }
    }

    private String parseEnvironmentValue(String raw) throws ConfigException {
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.startsWith("\"")) {
            if (!raw.endsWith("\"") || raw.length() == 1) {
                throw new ConfigException("unterminated double-quoted value");
            }
            return unescapeDoubleQuoted(raw.substring(1, raw.length() - 1));
        }
        if (raw.startsWith("'")) {
            if (!raw.endsWith("'") || raw.length() == 1) {
                throw new ConfigException("unterminated single-quoted value");
            }
            return raw.substring(1, raw.length() - 1);
        }
        int comment = findInlineComment(raw);
        return comment < 0 ? raw : raw.substring(0, comment).trim();
    }

    private int findInlineComment(String raw) {
        for (int index = 1; index < raw.length(); index++) {
            if (raw.charAt(index) == '#' && Character.isWhitespace(raw.charAt(index - 1))) {
                return index;
            }
        }
        return -1;
    }

    private String unescapeDoubleQuoted(String value) throws ConfigException {
        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaping) {
                if (current == '\\') {
                    escaping = true;
                } else {
                    result.append(current);
                }
                continue;
            }
            switch (current) {
                case 'n':
                    result.append('\n');
                    break;
                case 'r':
                    result.append('\r');
                    break;
                case 't':
                    result.append('\t');
                    break;
                case '"':
                    result.append('"');
                    break;
                case '\\':
                    result.append('\\');
                    break;
                default:
                    throw new ConfigException("unsupported escape sequence \\" + current);
            }
            escaping = false;
        }
        if (escaping) {
            throw new ConfigException("unfinished escape sequence");
        }
        return result.toString();
    }
}
