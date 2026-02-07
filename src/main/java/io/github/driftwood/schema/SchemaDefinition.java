package io.github.driftwood.schema;

import io.github.driftwood.compare.ArrayOrder;
import io.github.driftwood.core.ConfigException;
import io.github.driftwood.core.ConfigFormat;
import io.github.driftwood.model.ConfigNode;
import io.github.driftwood.model.DocumentLoader;
import io.github.driftwood.model.NodePath;
import io.github.driftwood.rules.Rule;
import io.github.driftwood.rules.RuleSet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SchemaDefinition {

    private final List<NodePath> requiredPaths;
    private final Map<NodePath, String> expectedTypes;
    private final Map<NodePath, ArrayOrder> arrayOrders;

    private SchemaDefinition(
            List<NodePath> requiredPaths,
            Map<NodePath, String> expectedTypes,
            Map<NodePath, ArrayOrder> arrayOrders) {
        this.requiredPaths = Collections.unmodifiableList(new ArrayList<>(requiredPaths));
        this.expectedTypes = Collections.unmodifiableMap(new LinkedHashMap<>(expectedTypes));
        this.arrayOrders = Collections.unmodifiableMap(new LinkedHashMap<>(arrayOrders));
    }

    public static SchemaDefinition empty() {
        return new SchemaDefinition(
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap());
    }

    public static SchemaDefinition load(Path path) throws ConfigException {
        if (path == null) {
            return empty();
        }
        ConfigNode root = new DocumentLoader().load(path, ConfigFormat.AUTO).getRoot();
        return parse(root, path);
    }

    public static SchemaDefinition parse(ConfigNode root, Path source) throws ConfigException {
        if (!root.isObject()) {
            throw new ConfigException("schema '" + source + "' must contain an object");
        }
        for (String key : root.asObject().keySet()) {
            if (!key.equals("required") && !key.equals("types") && !key.equals("arrays")) {
                throw new ConfigException(
                        "unknown schema section '" + key + "' in '" + source
                                + "'; expected required, types, or arrays");
            }
        }

        List<NodePath> required = parseRequired(root.asObject().get("required"), source);
        Map<NodePath, String> types = parseTypes(root.asObject().get("types"), source);
        Map<NodePath, ArrayOrder> arrays = parseArrays(root.asObject().get("arrays"), source);
        return new SchemaDefinition(required, types, arrays);
    }

    public List<NodePath> getRequiredPaths() {
        return requiredPaths;
    }

    public Map<NodePath, String> getExpectedTypes() {
        return expectedTypes;
    }

    public Map<NodePath, ArrayOrder> getArrayOrders() {
        return arrayOrders;
    }

    public RuleSet applyArrayRules(RuleSet base) {
        RuleSet result = base.copy();
        for (Map.Entry<NodePath, ArrayOrder> entry : arrayOrders.entrySet()) {
            result.add(Rule.arrayOrder(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private static List<NodePath> parseRequired(ConfigNode node, Path source) throws ConfigException {
        if (node == null) {
            return Collections.emptyList();
        }
        if (!node.isArray()) {
            throw new ConfigException("schema 'required' in '" + source + "' must be an array");
        }
        List<NodePath> result = new ArrayList<>();
        for (int index = 0; index < node.asArray().size(); index++) {
            ConfigNode value = node.asArray().get(index);
            if (!value.isScalar()) {
                throw new ConfigException(
                        "schema required entry at index " + index + " in '" + source
                                + "' must be a path string");
            }
            NodePath path = parseSchemaPath(value.asText(), "required", source);
            if (path.hasWildcard()) {
                throw new ConfigException("required schema paths must not contain wildcards: " + path);
            }
            result.add(path);
        }
        return result;
    }

    private static Map<NodePath, String> parseTypes(ConfigNode node, Path source) throws ConfigException {
        if (node == null) {
            return Collections.emptyMap();
        }
        if (!node.isObject()) {
            throw new ConfigException("schema 'types' in '" + source + "' must be an object");
        }
        Map<NodePath, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigNode> entry : node.asObject().entrySet()) {
            ConfigNode type = entry.getValue();
            if (!type.isScalar()) {
                throw new ConfigException(
                        "schema type for '" + entry.getKey() + "' in '" + source + "' must be a string");
            }
            NodePath path = parseSchemaPath(entry.getKey(), "types", source);
            if (path.hasWildcard()) {
                throw new ConfigException("schema type paths must not contain wildcards: " + path);
            }
            String typeName = type.asText().trim().toLowerCase(java.util.Locale.ROOT);
            validateTypeName(typeName, source, entry.getKey());
            result.put(path, typeName);
        }
        return result;
    }

    private static Map<NodePath, ArrayOrder> parseArrays(
            ConfigNode node,
            Path source) throws ConfigException {
        if (node == null) {
            return Collections.emptyMap();
        }
        if (!node.isObject()) {
            throw new ConfigException("schema 'arrays' in '" + source + "' must be an object");
        }
        Map<NodePath, ArrayOrder> result = new LinkedHashMap<>();
        for (Map.Entry<String, ConfigNode> entry : node.asObject().entrySet()) {
            ConfigNode orderNode = entry.getValue();
            if (!orderNode.isScalar()) {
                throw new ConfigException(
                        "schema array order for '" + entry.getKey() + "' in '" + source
                                + "' must be a string");
            }
            NodePath path = parseSchemaPath(entry.getKey(), "arrays", source);
            if (path.hasWildcard()) {
                throw new ConfigException("schema array paths must not contain wildcards: " + path);
            }
            try {
                result.put(path, ArrayOrder.fromName(orderNode.asText()));
            } catch (IllegalArgumentException e) {
                throw new ConfigException("invalid array order in '" + source + "': " + e.getMessage(), e);
            }
        }
        return result;
    }

    private static NodePath parseSchemaPath(String value, String section, Path source)
            throws ConfigException {
        try {
            return NodePath.parse(value);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(
                    "invalid schema path in section '" + section + "' of '" + source
                            + "': " + e.getMessage(),
                    e);
        }
    }

    private static void validateTypeName(String typeName, Path source, String path)
            throws ConfigException {
        switch (typeName) {
            case "object":
            case "array":
            case "string":
            case "number":
            case "integer":
            case "boolean":
            case "null":
            case "scalar":
                return;
            default:
                throw new ConfigException(
                        "unsupported schema type '" + typeName + "' for '" + path
                                + "' in '" + source + "'");
        }
    }
}
