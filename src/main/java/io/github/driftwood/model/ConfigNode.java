package io.github.driftwood.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable type-aware configuration value. Objects retain insertion order for rendering, while
 * comparison code sorts keys explicitly so results remain deterministic.
 */
public final class ConfigNode {

    public enum Type {
        OBJECT,
        ARRAY,
        SCALAR,
        NULL
    }

    private final Type type;
    private final Map<String, ConfigNode> object;
    private final List<ConfigNode> array;
    private final ScalarType scalarType;
    private final String scalar;

    private ConfigNode(
            Type type,
            Map<String, ConfigNode> object,
            List<ConfigNode> array,
            ScalarType scalarType,
            String scalar) {
        this.type = type;
        this.object = object;
        this.array = array;
        this.scalarType = scalarType;
        this.scalar = scalar;
    }

    public static ConfigNode object(Map<String, ConfigNode> values) {
        Objects.requireNonNull(values, "values");
        return new ConfigNode(
                Type.OBJECT,
                Collections.unmodifiableMap(new LinkedHashMap<>(values)),
                null,
                null,
                null);
    }

    public static ConfigNode emptyObject() {
        return object(Collections.emptyMap());
    }

    public static ConfigNode array(List<ConfigNode> values) {
        Objects.requireNonNull(values, "values");
        return new ConfigNode(
                Type.ARRAY,
                null,
                Collections.unmodifiableList(new ArrayList<>(values)),
                null,
                null);
    }

    public static ConfigNode scalar(String value, ScalarType type) {
        Objects.requireNonNull(type, "type");
        return new ConfigNode(Type.SCALAR, null, null, type, value);
    }

    public static ConfigNode string(String value) {
        return scalar(value, ScalarType.STRING);
    }

    public static ConfigNode number(String value) {
        return scalar(normalizeNumber(value), ScalarType.NUMBER);
    }

    public static ConfigNode bool(boolean value) {
        return scalar(Boolean.toString(value), ScalarType.BOOLEAN);
    }

    public static ConfigNode nullNode() {
        return new ConfigNode(Type.NULL, null, null, null, null);
    }

    public static ConfigNode fromJson(JsonNode value) {
        Objects.requireNonNull(value, "value");
        if (value.isNull() || value.isMissingNode()) {
            return nullNode();
        }
        if (value.isObject()) {
            Map<String, ConfigNode> fields = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> iterator = value.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> field = iterator.next();
                fields.put(field.getKey(), fromJson(field.getValue()));
            }
            return object(fields);
        }
        if (value.isArray()) {
            List<ConfigNode> items = new ArrayList<>();
            value.forEach(item -> items.add(fromJson(item)));
            return array(items);
        }
        if (value.isBoolean()) {
            return bool(value.booleanValue());
        }
        if (value.isNumber()) {
            return number(value.asText());
        }
        return string(value.asText());
    }

    public Type getType() {
        return type;
    }

    public boolean isObject() {
        return type == Type.OBJECT;
    }

    public boolean isArray() {
        return type == Type.ARRAY;
    }

    public boolean isScalar() {
        return type == Type.SCALAR;
    }

    public boolean isNull() {
        return type == Type.NULL;
    }

    public Map<String, ConfigNode> asObject() {
        if (!isObject()) {
            throw new IllegalStateException("node is not an object");
        }
        return object;
    }

    public List<ConfigNode> asArray() {
        if (!isArray()) {
            throw new IllegalStateException("node is not an array");
        }
        return array;
    }

    public ScalarType getScalarType() {
        if (!isScalar()) {
            throw new IllegalStateException("node is not a scalar");
        }
        return scalarType;
    }

    public String asText() {
        if (isNull()) {
            return null;
        }
        if (!isScalar()) {
            throw new IllegalStateException("node is not a scalar");
        }
        return scalar;
    }

    public boolean asBoolean() {
        if (type != Type.SCALAR || scalarType != ScalarType.BOOLEAN) {
            throw new IllegalStateException("node is not a boolean");
        }
        return Boolean.parseBoolean(scalar);
    }

    public BigDecimal asNumber() {
        if (type != Type.SCALAR || scalarType != ScalarType.NUMBER) {
            throw new IllegalStateException("node is not numeric");
        }
        return new BigDecimal(scalar);
    }

    public JsonNode toJson() {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        switch (type) {
            case OBJECT:
                ObjectNode objectNode = factory.objectNode();
                object.forEach((key, value) -> objectNode.set(key, value.toJson()));
                return objectNode;
            case ARRAY:
                ArrayNode arrayNode = factory.arrayNode();
                array.forEach(value -> arrayNode.add(value.toJson()));
                return arrayNode;
            case NULL:
                return factory.nullNode();
            case SCALAR:
                if (scalarType == ScalarType.BOOLEAN) {
                    return factory.booleanNode(Boolean.parseBoolean(scalar));
                }
                if (scalarType == ScalarType.NUMBER) {
                    try {
                        return factory.numberNode(new BigDecimal(scalar));
                    } catch (NumberFormatException ignored) {
                        return factory.textNode(scalar);
                    }
                }
                return factory.textNode(scalar);
            default:
                throw new IllegalStateException("unexpected node type: " + type);
        }
    }

    public String compactJson() {
        return toJson().toString();
    }

    public String displayText() {
        switch (type) {
            case NULL:
                return "null";
            case SCALAR:
                return "\"" + escape(displayScalar()) + "\"";
            default:
                return compactJson();
        }
    }

    public String displayScalar() {
        if (!isScalar()) {
            return compactJson();
        }
        return scalar;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigNode)) {
            return false;
        }
        ConfigNode that = (ConfigNode) other;
        if (type != that.type) {
            return false;
        }
        if (type == Type.SCALAR) {
            if (scalarType != that.scalarType) {
                return false;
            }
            if (scalarType == ScalarType.NUMBER) {
                return asNumber().compareTo(that.asNumber()) == 0;
            }
            return Objects.equals(scalar, that.scalar);
        }
        if (type == Type.OBJECT) {
            return object.equals(that.object);
        }
        if (type == Type.ARRAY) {
            return array.equals(that.array);
        }
        return true;
    }

    @Override
    public int hashCode() {
        if (type == Type.SCALAR && scalarType == ScalarType.NUMBER) {
            return Objects.hash(type, scalarType, asNumber().stripTrailingZeros());
        }
        return Objects.hash(type, object, array, scalarType, scalar);
    }

    @Override
    public String toString() {
        return compactJson();
    }

    private static String normalizeNumber(String value) {
        try {
            return new BigDecimal(value).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
