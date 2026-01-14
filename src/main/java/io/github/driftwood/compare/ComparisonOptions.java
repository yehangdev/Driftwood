package io.github.driftwood.compare;

import io.github.driftwood.model.NodePath;
import io.github.driftwood.model.PathMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComparisonOptions {

    private final List<NodePath> includes;
    private final List<NodePath> excludes;
    private final ArrayOrder defaultArrayOrder;
    private final int contextLines;
    private final boolean failOnIgnored;

    private ComparisonOptions(
            List<NodePath> includes,
            List<NodePath> excludes,
            ArrayOrder defaultArrayOrder,
            int contextLines,
            boolean failOnIgnored) {
        this.includes = Collections.unmodifiableList(new ArrayList<>(includes));
        this.excludes = Collections.unmodifiableList(new ArrayList<>(excludes));
        this.defaultArrayOrder = defaultArrayOrder;
        this.contextLines = contextLines;
        this.failOnIgnored = failOnIgnored;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<NodePath> getIncludes() {
        return includes;
    }

    public List<NodePath> getExcludes() {
        return excludes;
    }

    public ArrayOrder getDefaultArrayOrder() {
        return defaultArrayOrder;
    }

    public int getContextLines() {
        return contextLines;
    }

    public boolean isFailOnIgnored() {
        return failOnIgnored;
    }

    public boolean isSelected(NodePath path) {
        boolean included = includes.isEmpty()
                || includes.stream().anyMatch(pattern -> PathMatcher.matchesOrDescendant(pattern, path));
        if (!included) {
            return false;
        }
        return excludes.stream().noneMatch(pattern -> PathMatcher.matchesOrDescendant(pattern, path));
    }

    public static final class Builder {

        private final List<NodePath> includes = new ArrayList<>();
        private final List<NodePath> excludes = new ArrayList<>();
        private ArrayOrder defaultArrayOrder = ArrayOrder.ORDERED;
        private int contextLines;
        private boolean failOnIgnored;

        public Builder include(String path) {
            includes.add(NodePath.parse(path));
            return this;
        }

        public Builder include(NodePath path) {
            includes.add(path);
            return this;
        }

        public Builder exclude(String path) {
            excludes.add(NodePath.parse(path));
            return this;
        }

        public Builder exclude(NodePath path) {
            excludes.add(path);
            return this;
        }

        public Builder defaultArrayOrder(ArrayOrder order) {
            this.defaultArrayOrder = java.util.Objects.requireNonNull(order, "order");
            return this;
        }

        public Builder contextLines(int contextLines) {
            if (contextLines < 0) {
                throw new IllegalArgumentException("context lines must not be negative");
            }
            this.contextLines = contextLines;
            return this;
        }

        public Builder failOnIgnored(boolean failOnIgnored) {
            this.failOnIgnored = failOnIgnored;
            return this;
        }

        public ComparisonOptions build() {
            return new ComparisonOptions(
                    includes,
                    excludes,
                    defaultArrayOrder,
                    contextLines,
                    failOnIgnored);
        }
    }
}
