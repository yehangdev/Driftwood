package io.github.driftwood.model;

import io.github.driftwood.core.ConfigException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Dot and bracket path such as {@code server.ports[0]} or {@code services.*.url}.
 */
public final class NodePath {

    private final List<Segment> segments;

    private NodePath(List<Segment> segments) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    public static NodePath root() {
        return new NodePath(Collections.emptyList());
    }

    public static NodePath parse(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.startsWith("$")) {
            normalized = normalized.substring(1);
            if (normalized.startsWith(".")) {
                normalized = normalized.substring(1);
            }
        }

        List<Segment> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '.') {
                addToken(result, token);
            } else if (current == '[') {
                addToken(result, token);
                int end = normalized.indexOf(']', index);
                if (end < 0) {
                    throw new IllegalArgumentException("unclosed array selector in path: " + path);
                }
                String bracket = normalized.substring(index + 1, end).trim();
                result.add(segmentFromBracket(bracket, path));
                index = end;
            } else {
                token.append(current);
            }
        }
        addToken(result, token);
        return new NodePath(result);
    }

    public static NodePath parentOf(NodePath path) {
        if (path.segments.isEmpty()) {
            return path;
        }
        return new NodePath(path.segments.subList(0, path.segments.size() - 1));
    }

    public NodePath child(String key) {
        List<Segment> next = new ArrayList<>(segments);
        next.add(Segment.field(key));
        return new NodePath(next);
    }

    public NodePath index(int index) {
        List<Segment> next = new ArrayList<>(segments);
        next.add(Segment.index(index));
        return new NodePath(next);
    }

    public List<Segment> getSegments() {
        return segments;
    }

    public boolean isRoot() {
        return segments.isEmpty();
    }

    public String lastField() {
        if (segments.isEmpty() || segments.get(segments.size() - 1).getType() != Segment.Type.FIELD) {
            return null;
        }
        return segments.get(segments.size() - 1).getValue();
    }

    public boolean hasWildcard() {
        return segments.stream().anyMatch(segment -> segment.getType() == Segment.Type.WILDCARD
                || segment.getType() == Segment.Type.RECURSIVE);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Segment segment : segments) {
            switch (segment.getType()) {
                case FIELD:
                    if (result.length() > 0) {
                        result.append('.');
                    }
                    result.append(escapeField(segment.getValue()));
                    break;
                case INDEX:
                    result.append('[').append(segment.getValue()).append(']');
                    break;
                case WILDCARD:
                    if (result.length() > 0) {
                        result.append('.');
                    }
                    result.append('*');
                    break;
                case RECURSIVE:
                    if (result.length() > 0) {
                        result.append('.');
                    }
                    result.append("**");
                    break;
                default:
                    throw new IllegalStateException("unexpected path segment");
            }
        }
        return result.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NodePath)) {
            return false;
        }
        NodePath that = (NodePath) other;
        return segments.equals(that.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    private static void addToken(List<Segment> result, StringBuilder token) {
        if (token.length() == 0) {
            return;
        }
        String value = token.toString();
        token.setLength(0);
        if ("**".equals(value)) {
            result.add(Segment.recursive());
        } else if ("*".equals(value)) {
            result.add(Segment.wildcard());
        } else {
            result.add(Segment.field(value));
        }
    }

    private static Segment segmentFromBracket(String bracket, String originalPath) {
        if (bracket.isEmpty() || "*".equals(bracket) || "[]".equals(bracket)) {
            return Segment.wildcard();
        }
        if ("**".equals(bracket)) {
            return Segment.recursive();
        }
        try {
            int index = Integer.parseInt(bracket);
            if (index < 0) {
                throw new IllegalArgumentException("negative array index in path: " + originalPath);
            }
            return Segment.index(index);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid array index in path: " + originalPath);
        }
    }

    private static String escapeField(String field) {
        return field.replace("\\", "\\\\").replace(".", "\\.");
    }

    public static final class Segment {

        public enum Type {
            FIELD,
            INDEX,
            WILDCARD,
            RECURSIVE
        }

        private final Type type;
        private final String value;

        private Segment(Type type, String value) {
            this.type = type;
            this.value = value;
        }

        public static Segment field(String value) {
            return new Segment(Type.FIELD, Objects.requireNonNull(value, "value"));
        }

        public static Segment index(int value) {
            return new Segment(Type.INDEX, Integer.toString(value));
        }

        public static Segment wildcard() {
            return new Segment(Type.WILDCARD, "*");
        }

        public static Segment recursive() {
            return new Segment(Type.RECURSIVE, "**");
        }

        public Type getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Segment)) {
                return false;
            }
            Segment that = (Segment) other;
            return type == that.type && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, value);
        }
    }
}
