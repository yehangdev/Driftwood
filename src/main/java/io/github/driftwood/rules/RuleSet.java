package io.github.driftwood.rules;

import io.github.driftwood.compare.ArrayOrder;
import io.github.driftwood.core.ConfigException;
import io.github.driftwood.model.ConfigNode;
import io.github.driftwood.model.NodePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RuleSet {

    public static final String DEFAULT_MASK = "<masked>";

    private static final Pattern SENSITIVE_FIELD =
            Pattern.compile("(?i).*(password|secret|token|api[-_.]?key).*");

    private final List<Rule> rules;

    public RuleSet() {
        this.rules = new ArrayList<>();
    }

    private RuleSet(List<Rule> rules) {
        this.rules = new ArrayList<>(rules);
    }

    public static RuleSet defaults(boolean sensitiveMasking, String replacement) {
        RuleSet result = new RuleSet();
        if (sensitiveMasking) {
            result.add(Rule.sensitive(NodePath.parse("**"), SENSITIVE_FIELD, replacement));
        }
        return result;
    }

    public void add(Rule rule) {
        rules.add(java.util.Objects.requireNonNull(rule, "rule"));
    }

    public RuleSet copy() {
        return new RuleSet(rules);
    }

    public List<Rule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public boolean shouldIgnore(NodePath path) {
        return rules.stream()
                .anyMatch(rule -> rule.getAction() == Rule.Action.IGNORE
                        && rule.matchesOrDescendant(path));
    }

    public boolean shouldMask(NodePath path) {
        return rules.stream()
                .filter(rule -> rule.getAction() == Rule.Action.MASK)
                .anyMatch(rule -> rule.matches(path));
    }

    public String maskValue(NodePath path, ConfigNode value) {
        for (Rule rule : rules) {
            if (rule.getAction() == Rule.Action.MASK && rule.matches(path)) {
                return rule.getReplacement();
            }
        }
        return value == null ? null : value.displayText();
    }

    public ArrayOrder arrayOrder(NodePath path, ArrayOrder fallback) {
        ArrayOrder result = fallback;
        for (Rule rule : rules) {
            if (rule.getAction() == Rule.Action.ARRAY_ORDER && rule.matches(path)) {
                result = rule.getArrayOrder();
            }
        }
        return result;
    }

    public static RuleSet load(Path path, RuleSet base) throws ConfigException {
        if (path == null) {
            return base.copy();
        }
        if (!Files.isRegularFile(path)) {
            throw new ConfigException("rule file does not exist: " + path);
        }

        RuleSet result = base.copy();
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                String line = stripComment(lines.get(index)).trim();
                if (line.isEmpty()) {
                    continue;
                }
                result.add(parseRule(line, path, index + 1));
            }
        } catch (IOException e) {
            throw new ConfigException("failed to read rule file '" + path + "': " + e.getMessage(), e);
        }
        return result;
    }

    public static Rule parseRule(String line, Path source, int lineNumber) throws ConfigException {
        List<String> tokens = tokenize(line, source, lineNumber);
        if (tokens.size() < 2) {
            throw ruleError(source, lineNumber, "expected an action and a path");
        }

        String action = tokens.get(0).toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "ignore":
                    rejectExtra(tokens, 2, source, lineNumber);
                    return Rule.ignore(NodePath.parse(tokens.get(1)));
                case "mask":
                    if (tokens.size() > 3) {
                        throw ruleError(source, lineNumber, "mask accepts a path and optional replacement");
                    }
                    String replacement = tokens.size() == 3 ? tokens.get(2) : DEFAULT_MASK;
                    return Rule.mask(NodePath.parse(tokens.get(1)), replacement);
                case "ordered":
                    rejectExtra(tokens, 2, source, lineNumber);
                    return Rule.arrayOrder(NodePath.parse(tokens.get(1)), ArrayOrder.ORDERED);
                case "unordered":
                    rejectExtra(tokens, 2, source, lineNumber);
                    return Rule.arrayOrder(NodePath.parse(tokens.get(1)), ArrayOrder.UNORDERED);
                case "array-order":
                    if (tokens.size() != 3) {
                        throw ruleError(source, lineNumber, "array-order requires a path and an order");
                    }
                    return Rule.arrayOrder(
                            NodePath.parse(tokens.get(1)),
                            ArrayOrder.fromName(tokens.get(2)));
                default:
                    throw ruleError(source, lineNumber, "unknown rule action '" + tokens.get(0) + "'");
            }
        } catch (IllegalArgumentException e) {
            throw ruleError(source, lineNumber, e.getMessage());
        }
    }

    private static List<String> tokenize(String line, Path source, int lineNumber) throws ConfigException {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        boolean escaping = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (escaping) {
                current.append(character);
                escaping = false;
                continue;
            }
            if (character == '\\') {
                escaping = true;
                continue;
            }
            if (character == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
                continue;
            }
            if (character == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
                continue;
            }
            if (Character.isWhitespace(character) && !singleQuoted && !doubleQuoted) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(character);
            }
        }
        if (singleQuoted || doubleQuoted || escaping) {
            throw ruleError(source, lineNumber, "unterminated quote or escape");
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String stripComment(String line) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (current == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (current == '#' && !singleQuoted && !doubleQuoted) {
                return line.substring(0, index);
            }
        }
        return line;
    }

    private static void rejectExtra(
            List<String> tokens,
            int expected,
            Path source,
            int lineNumber) throws ConfigException {
        if (tokens.size() != expected) {
            throw ruleError(source, lineNumber, "unexpected trailing arguments");
        }
    }

    private static ConfigException ruleError(Path source, int lineNumber, String message) {
        return new ConfigException(
                "invalid rule in '" + source + "' at line " + lineNumber + ": " + message);
    }
}
