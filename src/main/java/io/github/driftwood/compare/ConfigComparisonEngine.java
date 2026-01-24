package io.github.driftwood.compare;

import io.github.driftwood.core.ConfigException;
import io.github.driftwood.model.ConfigDocument;
import io.github.driftwood.model.ConfigNode;
import io.github.driftwood.model.DocumentLoader;
import io.github.driftwood.model.DocumentMerger;
import io.github.driftwood.model.NodePath;
import io.github.driftwood.rules.RuleSet;
import io.github.driftwood.rules.Rule;
import io.github.driftwood.schema.SchemaDefinition;
import io.github.driftwood.schema.SchemaValidator;

import java.util.List;
import java.util.Objects;

public final class ConfigComparisonEngine {

    private final DocumentLoader loader;
    private final SchemaValidator validator;

    public ConfigComparisonEngine() {
        this(new DocumentLoader(), new SchemaValidator());
    }

    ConfigComparisonEngine(DocumentLoader loader, SchemaValidator validator) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public ComparisonResult compare(ComparisonRequest request) throws ConfigException {
        Context context = prepare(request);
        return new ConfigTreeDiffer(request.getOptions(), context.rules)
                .compare(context.left, context.right);
    }

    public void validate(ComparisonRequest request) throws ConfigException {
        prepare(request);
    }

    private Context prepare(ComparisonRequest request) throws ConfigException {
        RuleSet rules = RuleSet.defaults(
                request.isSensitiveMasking(),
                request.getMaskReplacement());
        rules = RuleSet.load(request.getRulesFile(), rules);
        for (String pattern : request.getIgnorePatterns()) {
            rules.add(Rule.ignore(NodePath.parse(pattern)));
        }
        for (String pattern : request.getMaskPatterns()) {
            rules.add(Rule.mask(NodePath.parse(pattern), request.getMaskReplacement()));
        }
        for (String path : request.getOrderedPaths()) {
            rules.add(Rule.arrayOrder(
                    NodePath.parse(path),
                    ArrayOrder.ORDERED));
        }
        for (String path : request.getUnorderedPaths()) {
            rules.add(Rule.arrayOrder(
                    NodePath.parse(path),
                    ArrayOrder.UNORDERED));
        }

        SchemaDefinition schema = SchemaDefinition.load(request.getSchemaFile());
        rules = schema.applyArrayRules(rules);

        ConfigDocument leftDocument = loader.load(request.getLeft(), request.getLeftFormat());
        ConfigDocument rightDocument = loader.load(request.getRight(), request.getRightFormat());
        ConfigNode left = applyOverlay(
                leftDocument.getRoot(),
                request.getLeftOverlay(),
                request.getLeftOverlayFormat(),
                "left");
        ConfigNode right = applyOverlay(
                rightDocument.getRoot(),
                request.getRightOverlay(),
                request.getRightOverlayFormat(),
                "right");

        validateDocument(new ConfigDocument(
                leftDocument.getSource(),
                leftDocument.getFormat(),
                left), schema);
        validateDocument(new ConfigDocument(
                rightDocument.getSource(),
                rightDocument.getFormat(),
                right), schema);

        left = applyProfile(left, request.getProfile(), "left");
        right = applyProfile(right, request.getProfile(), "right");
        return new Context(left, right, rules);
    }

    private ConfigNode applyOverlay(
            ConfigNode base,
            java.nio.file.Path overlayPath,
            io.github.driftwood.core.ConfigFormat format,
            String side) throws ConfigException {
        if (overlayPath == null) {
            return base;
        }
        ConfigNode overlay = loader.load(overlayPath, format).getRoot();
        if (!overlay.isObject()) {
            throw new ConfigException(side + " overlay must contain an object: " + overlayPath);
        }
        return DocumentMerger.merge(base, overlay);
    }

    private ConfigNode applyProfile(ConfigNode root, String profile, String side)
            throws ConfigException {
        if (profile == null || profile.trim().isEmpty()) {
            return root;
        }

        NodePath requested = NodePath.parse(profile);
        if (requested.hasWildcard()) {
            throw new ConfigException("profile path must not contain wildcards: " + profile);
        }
        ConfigNode selected = locate(root, requested);
        if (selected == null && !requested.toString().startsWith("profiles.")) {
            selected = locate(root, NodePath.parse("profiles." + profile));
        }
        if (selected == null) {
            throw new ConfigException(
                    "profile '" + profile + "' does not exist in the " + side + " configuration");
        }
        if (!selected.isObject()) {
            throw new ConfigException(
                    "profile '" + profile + "' in the " + side + " configuration is not an object");
        }
        return selected;
    }

    private void validateDocument(ConfigDocument document, SchemaDefinition schema)
            throws ConfigException {
        List<String> issues = validator.validate(document, schema);
        if (!issues.isEmpty()) {
            throw new ConfigException("validation failed: " + String.join("; ", issues));
        }
    }

    private ConfigNode locate(ConfigNode root, NodePath path) {
        ConfigNode current = root;
        for (NodePath.Segment segment : path.getSegments()) {
            if (segment.getType() == NodePath.Segment.Type.FIELD) {
                if (!current.isObject()) {
                    return null;
                }
                current = current.asObject().get(segment.getValue());
            } else if (segment.getType() == NodePath.Segment.Type.INDEX) {
                if (!current.isArray()) {
                    return null;
                }
                int index = Integer.parseInt(segment.getValue());
                if (index >= current.asArray().size()) {
                    return null;
                }
                current = current.asArray().get(index);
            } else {
                return null;
            }
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static final class Context {

        private final ConfigNode left;
        private final ConfigNode right;
        private final RuleSet rules;

        private Context(ConfigNode left, ConfigNode right, RuleSet rules) {
            this.left = left;
            this.right = right;
            this.rules = rules;
        }
    }
}
