package io.github.driftwood.compare;

import io.github.driftwood.model.ConfigNode;
import io.github.driftwood.model.NodePath;
import io.github.driftwood.rules.RuleSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class ConfigTreeDiffer {

    private final ComparisonOptions options;
    private final RuleSet rules;
    private final List<ConfigChange> changes = new ArrayList<>();
    private int ignoredCount;
    private ConfigNode leftRoot;
    private ConfigNode rightRoot;

    public ConfigTreeDiffer(ComparisonOptions options, RuleSet rules) {
        this.options = Objects.requireNonNull(options, "options");
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public ComparisonResult compare(ConfigNode left, ConfigNode right) {
        changes.clear();
        ignoredCount = 0;
        leftRoot = left;
        rightRoot = right;
        compareNode(NodePath.root(), left, right);
        changes.sort(Comparator
                .comparing((ConfigChange change) -> change.getPath().toString())
                .thenComparing(change -> change.getType().name()));
        return new ComparisonResult(left, right, changes, ignoredCount, options);
    }

    private void compareNode(NodePath path, ConfigNode left, ConfigNode right) {
        if (rules.shouldIgnore(path)) {
            ignoredCount++;
            return;
        }

        if (left != null && right != null && left.getType() == right.getType()) {
            if (left.isObject()) {
                compareObjects(path, left, right);
                return;
            }
            if (left.isArray()) {
                compareArrays(path, left, right);
                return;
            }
        }

        if (left == null) {
            if (options.isSelected(path)) {
                add(ConfigChange.Type.ADDED, path, null, right);
            }
            return;
        }
        if (right == null) {
            if (options.isSelected(path)) {
                add(ConfigChange.Type.REMOVED, path, left, null);
            }
            return;
        }
        if (!left.equals(right) && options.isSelected(path)) {
            add(ConfigChange.Type.CHANGED, path, left, right);
        }
    }

    private void compareObjects(NodePath path, ConfigNode left, ConfigNode right) {
        Set<String> keys = new TreeSet<>();
        keys.addAll(left.asObject().keySet());
        keys.addAll(right.asObject().keySet());
        for (String key : keys) {
            compareNode(
                    path.child(key),
                    left.asObject().get(key),
                    right.asObject().get(key));
        }
    }

    private void compareArrays(NodePath path, ConfigNode left, ConfigNode right) {
        ArrayOrder order = rules.arrayOrder(path, options.getDefaultArrayOrder());
        if (order == ArrayOrder.UNORDERED) {
            compareUnorderedArray(path, left, right);
        } else {
            compareOrderedArray(path, left, right);
        }
    }

    private void compareOrderedArray(NodePath path, ConfigNode left, ConfigNode right) {
        List<ConfigNode> leftItems = left.asArray();
        List<ConfigNode> rightItems = right.asArray();
        if (leftItems.size() != rightItems.size() && options.isSelected(path)) {
            add(ConfigChange.Type.CHANGED, path, left, right);
        }
        int common = Math.min(leftItems.size(), rightItems.size());
        for (int index = 0; index < common; index++) {
            compareNode(path.index(index), leftItems.get(index), rightItems.get(index));
        }
        for (int index = common; index < leftItems.size(); index++) {
            compareNode(path.index(index), leftItems.get(index), null);
        }
        for (int index = common; index < rightItems.size(); index++) {
            compareNode(path.index(index), null, rightItems.get(index));
        }
    }

    private void compareUnorderedArray(NodePath path, ConfigNode left, ConfigNode right) {
        List<ConfigNode> leftItems = left.asArray();
        List<ConfigNode> rightItems = right.asArray();
        boolean[] matchedRight = new boolean[rightItems.size()];

        for (int leftIndex = 0; leftIndex < leftItems.size(); leftIndex++) {
            ConfigNode item = leftItems.get(leftIndex);
            int match = findMatch(item, rightItems, matchedRight);
            if (match < 0) {
                NodePath itemPath = path.index(leftIndex);
                if (options.isSelected(itemPath) && !rules.shouldIgnore(itemPath)) {
                    add(ConfigChange.Type.REMOVED, itemPath, item, null);
                } else if (rules.shouldIgnore(itemPath)) {
                    ignoredCount++;
                }
            } else {
                matchedRight[match] = true;
            }
        }

        for (int rightIndex = 0; rightIndex < rightItems.size(); rightIndex++) {
            if (!matchedRight[rightIndex]) {
                NodePath itemPath = path.index(rightIndex);
                if (options.isSelected(itemPath) && !rules.shouldIgnore(itemPath)) {
                    add(ConfigChange.Type.ADDED, itemPath, null, rightItems.get(rightIndex));
                } else if (rules.shouldIgnore(itemPath)) {
                    ignoredCount++;
                }
            }
        }
    }

    private int findMatch(ConfigNode item, List<ConfigNode> candidates, boolean[] used) {
        for (int index = 0; index < candidates.size(); index++) {
            if (!used[index] && item.equals(candidates.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private void add(ConfigChange.Type type, NodePath path, ConfigNode left, ConfigNode right) {
        String leftDisplay = left == null ? null : rules.maskValue(path, left);
        String rightDisplay = right == null ? null : rules.maskValue(path, right);
        Map<NodePath, String> context = options.getContextLines() == 0
                ? Collections.emptyMap()
                : contextFor(path);
        changes.add(new ConfigChange(
                type,
                path,
                left,
                right,
                leftDisplay,
                rightDisplay,
                context));
    }

    private Map<NodePath, String> contextFor(NodePath changedPath) {
        if (changedPath.isRoot()) {
            return Collections.emptyMap();
        }
        NodePath parent = NodePath.parentOf(changedPath);
        List<ContextValue> values = new ArrayList<>();
        collectContext(parent, findNode(parent, leftRoot, rightRoot), values, options.getContextLines());
        values.removeIf(value -> value.path.equals(changedPath));
        values.sort(Comparator.comparing(value -> value.path.toString()));
        if (values.size() > options.getContextLines()) {
            values = new ArrayList<>(values.subList(0, options.getContextLines()));
        }
        Map<NodePath, String> result = new LinkedHashMap<>();
        for (ContextValue value : values) {
            result.put(value.path, value.display);
        }
        return result;
    }

    private ConfigNode findNode(NodePath path, ConfigNode left, ConfigNode right) {
        ConfigNode found = locate(path, left);
        return found == null ? locate(path, right) : found;
    }

    private ConfigNode locate(NodePath path, ConfigNode root) {
        if (root == null) {
            return null;
        }
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

    private void collectContext(
            NodePath path,
            ConfigNode node,
            List<ContextValue> context,
            int limit) {
        if (node == null || context.size() >= limit * 4) {
            return;
        }
        if (!node.isObject()) {
            context.add(new ContextValue(path, rules.maskValue(path, node)));
            return;
        }
        for (Map.Entry<String, ConfigNode> entry : node.asObject().entrySet()) {
            collectContext(path.child(entry.getKey()), entry.getValue(), context, limit);
        }
    }

    private static final class ContextValue {

        private final NodePath path;
        private final String display;

        private ContextValue(NodePath path, String display) {
            this.path = path;
            this.display = display;
        }
    }
}
