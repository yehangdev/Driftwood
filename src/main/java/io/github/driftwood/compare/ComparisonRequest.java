package io.github.driftwood.compare;

import io.github.driftwood.core.ConfigFormat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ComparisonRequest {

    private final Path left;
    private final Path right;
    private final ConfigFormat leftFormat;
    private final ConfigFormat rightFormat;
    private final Path leftOverlay;
    private final Path rightOverlay;
    private final ConfigFormat leftOverlayFormat;
    private final ConfigFormat rightOverlayFormat;
    private final String profile;
    private final Path rulesFile;
    private final Path schemaFile;
    private final List<String> ignorePatterns;
    private final List<String> maskPatterns;
    private final List<String> orderedPaths;
    private final List<String> unorderedPaths;
    private final ComparisonOptions options;
    private final boolean sensitiveMasking;
    private final String maskReplacement;

    private ComparisonRequest(Builder builder) {
        this.left = builder.left;
        this.right = builder.right;
        this.leftFormat = builder.leftFormat;
        this.rightFormat = builder.rightFormat;
        this.leftOverlay = builder.leftOverlay;
        this.rightOverlay = builder.rightOverlay;
        this.leftOverlayFormat = builder.leftOverlayFormat;
        this.rightOverlayFormat = builder.rightOverlayFormat;
        this.profile = builder.profile;
        this.rulesFile = builder.rulesFile;
        this.schemaFile = builder.schemaFile;
        this.ignorePatterns = Collections.unmodifiableList(new ArrayList<>(builder.ignorePatterns));
        this.maskPatterns = Collections.unmodifiableList(new ArrayList<>(builder.maskPatterns));
        this.orderedPaths = Collections.unmodifiableList(new ArrayList<>(builder.orderedPaths));
        this.unorderedPaths = Collections.unmodifiableList(new ArrayList<>(builder.unorderedPaths));
        this.options = builder.options;
        this.sensitiveMasking = builder.sensitiveMasking;
        this.maskReplacement = builder.maskReplacement;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Path getLeft() {
        return left;
    }

    public Path getRight() {
        return right;
    }

    public ConfigFormat getLeftFormat() {
        return leftFormat;
    }

    public ConfigFormat getRightFormat() {
        return rightFormat;
    }

    public Path getLeftOverlay() {
        return leftOverlay;
    }

    public Path getRightOverlay() {
        return rightOverlay;
    }

    public ConfigFormat getLeftOverlayFormat() {
        return leftOverlayFormat;
    }

    public ConfigFormat getRightOverlayFormat() {
        return rightOverlayFormat;
    }

    public String getProfile() {
        return profile;
    }

    public Path getRulesFile() {
        return rulesFile;
    }

    public Path getSchemaFile() {
        return schemaFile;
    }

    public List<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    public List<String> getMaskPatterns() {
        return maskPatterns;
    }

    public List<String> getOrderedPaths() {
        return orderedPaths;
    }

    public List<String> getUnorderedPaths() {
        return unorderedPaths;
    }

    public ComparisonOptions getOptions() {
        return options;
    }

    public boolean isSensitiveMasking() {
        return sensitiveMasking;
    }

    public String getMaskReplacement() {
        return maskReplacement;
    }

    public static final class Builder {

        private Path left;
        private Path right;
        private ConfigFormat leftFormat = ConfigFormat.AUTO;
        private ConfigFormat rightFormat = ConfigFormat.AUTO;
        private Path leftOverlay;
        private Path rightOverlay;
        private ConfigFormat leftOverlayFormat = ConfigFormat.AUTO;
        private ConfigFormat rightOverlayFormat = ConfigFormat.AUTO;
        private String profile;
        private Path rulesFile;
        private Path schemaFile;
        private final List<String> ignorePatterns = new ArrayList<>();
        private final List<String> maskPatterns = new ArrayList<>();
        private final List<String> orderedPaths = new ArrayList<>();
        private final List<String> unorderedPaths = new ArrayList<>();
        private ComparisonOptions options = ComparisonOptions.builder().build();
        private boolean sensitiveMasking = true;
        private String maskReplacement = "<redacted>";

        public Builder left(Path left) {
            this.left = left;
            return this;
        }

        public Builder right(Path right) {
            this.right = right;
            return this;
        }

        public Builder leftFormat(ConfigFormat leftFormat) {
            this.leftFormat = leftFormat;
            return this;
        }

        public Builder rightFormat(ConfigFormat rightFormat) {
            this.rightFormat = rightFormat;
            return this;
        }

        public Builder leftOverlay(Path leftOverlay) {
            this.leftOverlay = leftOverlay;
            return this;
        }

        public Builder rightOverlay(Path rightOverlay) {
            this.rightOverlay = rightOverlay;
            return this;
        }

        public Builder leftOverlayFormat(ConfigFormat format) {
            this.leftOverlayFormat = format;
            return this;
        }

        public Builder rightOverlayFormat(ConfigFormat format) {
            this.rightOverlayFormat = format;
            return this;
        }

        public Builder profile(String profile) {
            this.profile = profile;
            return this;
        }

        public Builder rulesFile(Path rulesFile) {
            this.rulesFile = rulesFile;
            return this;
        }

        public Builder schemaFile(Path schemaFile) {
            this.schemaFile = schemaFile;
            return this;
        }

        public Builder ignore(String pattern) {
            this.ignorePatterns.add(Objects.requireNonNull(pattern, "pattern"));
            return this;
        }

        public Builder mask(String pattern) {
            this.maskPatterns.add(Objects.requireNonNull(pattern, "pattern"));
            return this;
        }

        public Builder orderedPath(String path) {
            this.orderedPaths.add(Objects.requireNonNull(path, "path"));
            return this;
        }

        public Builder unorderedPath(String path) {
            this.unorderedPaths.add(Objects.requireNonNull(path, "path"));
            return this;
        }

        public Builder options(ComparisonOptions options) {
            this.options = Objects.requireNonNull(options, "options");
            return this;
        }

        public Builder sensitiveMasking(boolean sensitiveMasking) {
            this.sensitiveMasking = sensitiveMasking;
            return this;
        }

        public Builder maskReplacement(String maskReplacement) {
            this.maskReplacement = Objects.requireNonNull(maskReplacement, "maskReplacement");
            return this;
        }

        public ComparisonRequest build() {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
            Objects.requireNonNull(leftFormat, "leftFormat");
            Objects.requireNonNull(rightFormat, "rightFormat");
            Objects.requireNonNull(options, "options");
            return new ComparisonRequest(this);
        }
    }
}
