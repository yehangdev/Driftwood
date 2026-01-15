package io.github.driftwood.compare;

import io.github.driftwood.model.ConfigNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ComparisonResult {

    private final ConfigNode leftRoot;
    private final ConfigNode rightRoot;
    private final List<ConfigChange> changes;
    private final int ignoredCount;
    private final ComparisonOptions options;

    public ComparisonResult(
            ConfigNode leftRoot,
            ConfigNode rightRoot,
            List<ConfigChange> changes,
            int ignoredCount,
            ComparisonOptions options) {
        this.leftRoot = leftRoot;
        this.rightRoot = rightRoot;
        this.changes = Collections.unmodifiableList(new ArrayList<>(changes));
        this.ignoredCount = ignoredCount;
        this.options = options;
    }

    public ConfigNode getLeftRoot() {
        return leftRoot;
    }

    public ConfigNode getRightRoot() {
        return rightRoot;
    }

    public List<ConfigChange> getChanges() {
        return changes;
    }

    public int getIgnoredCount() {
        return ignoredCount;
    }

    public ComparisonOptions getOptions() {
        return options;
    }

    public boolean isEqual() {
        return changes.isEmpty();
    }

    public long count(ConfigChange.Type type) {
        return changes.stream().filter(change -> change.getType() == type).count();
    }
}
