package io.github.driftwood.rules;

import io.github.driftwood.compare.ArrayOrder;
import io.github.driftwood.model.NodePath;
import io.github.driftwood.model.PathMatcher;

import java.util.Objects;
import java.util.regex.Pattern;

public final class Rule {

    public enum Action {
        IGNORE,
        MASK,
        ARRAY_ORDER
    }

    private final Action action;
    private final NodePath pathPattern;
    private final Pattern fieldPattern;
    private final String replacement;
    private final ArrayOrder arrayOrder;

    private Rule(
            Action action,
            NodePath pathPattern,
            Pattern fieldPattern,
            String replacement,
            ArrayOrder arrayOrder) {
        this.action = Objects.requireNonNull(action, "action");
        this.pathPattern = pathPattern;
        this.fieldPattern = fieldPattern;
        this.replacement = replacement;
        this.arrayOrder = arrayOrder;
    }

    public static Rule ignore(NodePath pattern) {
        return new Rule(Action.IGNORE, pattern, null, null, null);
    }

    public static Rule mask(NodePath pattern, String replacement) {
        return new Rule(Action.MASK, pattern, null, replacement, null);
    }

    public static Rule sensitive(NodePath pattern, Pattern fieldPattern, String replacement) {
        return new Rule(Action.MASK, pattern, fieldPattern, replacement, null);
    }

    public static Rule arrayOrder(NodePath pattern, ArrayOrder order) {
        return new Rule(Action.ARRAY_ORDER, pattern, null, null, order);
    }

    public Action getAction() {
        return action;
    }

    public NodePath getPathPattern() {
        return pathPattern;
    }

    public String getReplacement() {
        return replacement;
    }

    public ArrayOrder getArrayOrder() {
        return arrayOrder;
    }

    public boolean matches(NodePath path) {
        if (!PathMatcher.matches(pathPattern, path)) {
            return false;
        }
        if (fieldPattern == null) {
            return true;
        }
        String last = path.lastField();
        return last != null && fieldPattern.matcher(last).matches();
    }

    public boolean matchesOrDescendant(NodePath path) {
        if (PathMatcher.matchesOrDescendant(pathPattern, path)) {
            if (fieldPattern == null) {
                return true;
            }
            String last = path.lastField();
            return last != null && fieldPattern.matcher(last).matches();
        }
        return false;
    }
}
