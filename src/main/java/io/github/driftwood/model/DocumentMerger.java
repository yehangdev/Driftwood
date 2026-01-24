package io.github.driftwood.model;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DocumentMerger {

    private DocumentMerger() {
    }

    public static ConfigNode merge(ConfigNode base, ConfigNode overlay) {
        if (!base.isObject() || !overlay.isObject()) {
            return overlay;
        }

        Map<String, ConfigNode> merged = new LinkedHashMap<>(base.asObject());
        for (Map.Entry<String, ConfigNode> entry : overlay.asObject().entrySet()) {
            ConfigNode current = merged.get(entry.getKey());
            if (current != null && current.isObject() && entry.getValue().isObject()) {
                merged.put(entry.getKey(), merge(current, entry.getValue()));
            } else {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return ConfigNode.object(merged);
    }
}
