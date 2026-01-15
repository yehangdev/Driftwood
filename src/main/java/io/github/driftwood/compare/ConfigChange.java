package io.github.driftwood.compare;

import io.github.driftwood.model.ConfigNode;
import io.github.driftwood.model.NodePath;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ConfigChange {

    public enum Type {
        ADDED,
        REMOVED,
        CHANGED
    }

    private final Type type;
    private final NodePath path;
    private final ConfigNode left;
    private final ConfigNode right;
    private final String leftDisplay;
    private final String rightDisplay;
    private final Map<NodePath, String> context;

    public ConfigChange(
            Type type,
            NodePath path,
            ConfigNode left,
            ConfigNode right,
            String leftDisplay,
            String rightDisplay,
            Map<NodePath, String> context) {
        this.type = Objects.requireNonNull(type, "type");
        this.path = Objects.requireNonNull(path, "path");
        this.left = left;
        this.right = right;
        this.leftDisplay = leftDisplay;
        this.rightDisplay = rightDisplay;
        this.context = Collections.unmodifiableMap(new LinkedHashMap<>(context));
    }

    public Type getType() {
        return type;
    }

    public NodePath getPath() {
        return path;
    }

    public ConfigNode getLeft() {
        return left;
    }

    public ConfigNode getRight() {
        return right;
    }

    public String getLeftDisplay() {
        return leftDisplay;
    }

    public String getRightDisplay() {
        return rightDisplay;
    }

    public Map<NodePath, String> getContext() {
        return context;
    }
}
