package io.github.driftwood.model;

import io.github.driftwood.core.ConfigFormat;

import java.nio.file.Path;
import java.util.Objects;

public final class ConfigDocument {

    private final Path source;
    private final ConfigFormat format;
    private final ConfigNode root;

    public ConfigDocument(Path source, ConfigFormat format, ConfigNode root) {
        this.source = Objects.requireNonNull(source, "source");
        this.format = Objects.requireNonNull(format, "format");
        this.root = Objects.requireNonNull(root, "root");
    }

    public Path getSource() {
        return source;
    }

    public ConfigFormat getFormat() {
        return format;
    }

    public ConfigNode getRoot() {
        return root;
    }
}
