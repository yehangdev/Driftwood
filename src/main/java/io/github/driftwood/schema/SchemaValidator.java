package io.github.driftwood.schema;

import io.github.driftwood.model.ConfigDocument;
import io.github.driftwood.model.ConfigNode;
import io.github.driftwood.model.NodePath;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class SchemaValidator {

    public List<String> validate(ConfigDocument document, SchemaDefinition schema) {
        if (schema == null
                || (schema.getRequiredPaths().isEmpty()
                && schema.getExpectedTypes().isEmpty()
                && schema.getArrayOrders().isEmpty())) {
            return Collections.emptyList();
        }
        List<String> issues = new ArrayList<>();
        for (NodePath required : schema.getRequiredPaths()) {
            if (locate(document.getRoot(), required) == null) {
                issues.add(document.getSource() + ": missing required path '" + required + "'");
            }
        }
        for (Map.Entry<NodePath, String> entry : schema.getExpectedTypes().entrySet()) {
            ConfigNode value = locate(document.getRoot(), entry.getKey());
            if (value != null && !matchesType(value, entry.getValue())) {
                issues.add(
                        document.getSource()
                                + ": path '" + entry.getKey() + "' must be "
                                + entry.getValue() + " but was " + describe(value));
            }
        }
        return issues;
    }

    public ConfigNode locate(ConfigNode root, NodePath path) {
        ConfigNode current = root;
        for (NodePath.Segment segment : path.getSegments()) {
            if (segment.getType() != NodePath.Segment.Type.FIELD
                    && segment.getType() != NodePath.Segment.Type.INDEX) {
                return null;
            }
            if (segment.getType() == NodePath.Segment.Type.FIELD) {
                if (!current.isObject()) {
                    return null;
                }
                current = current.asObject().get(segment.getValue());
            } else {
                if (!current.isArray()) {
                    return null;
                }
                int index = Integer.parseInt(segment.getValue());
                if (index >= current.asArray().size()) {
                    return null;
                }
                current = current.asArray().get(index);
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private boolean matchesType(ConfigNode node, String expected) {
        switch (expected) {
            case "object":
                return node.isObject();
            case "array":
                return node.isArray();
            case "string":
                return node.isScalar()
                        && node.getScalarType() == io.github.driftwood.model.ScalarType.STRING;
            case "number":
                return node.isScalar()
                        && node.getScalarType() == io.github.driftwood.model.ScalarType.NUMBER;
            case "integer":
                if (!node.isScalar()
                        || node.getScalarType() != io.github.driftwood.model.ScalarType.NUMBER) {
                    return false;
                }
                BigDecimal number = node.asNumber();
                return number.stripTrailingZeros().scale() <= 0;
            case "boolean":
                return node.isScalar()
                        && node.getScalarType() == io.github.driftwood.model.ScalarType.BOOLEAN;
            case "null":
                return node.isNull();
            case "scalar":
                return node.isScalar() || node.isNull();
            default:
                return false;
        }
    }

    private String describe(ConfigNode node) {
        if (node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        return node.getScalarType().name().toLowerCase(java.util.Locale.ROOT);
    }
}
