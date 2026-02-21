package io.github.driftwood.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.driftwood.compare.ComparisonResult;
import io.github.driftwood.compare.ConfigChange;
import io.github.driftwood.model.NodePath;

import java.io.PrintWriter;
import java.util.Map;

public final class JsonReportFormatter implements ReportFormatter {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void write(ComparisonResult result, PrintWriter out) {
        ObjectNode root = mapper.createObjectNode();
        root.put("equal", result.isEqual());

        ObjectNode summary = root.putObject("summary");
        summary.put("added", result.count(ConfigChange.Type.ADDED));
        summary.put("removed", result.count(ConfigChange.Type.REMOVED));
        summary.put("changed", result.count(ConfigChange.Type.CHANGED));
        summary.put("ignored", result.getIgnoredCount());

        ArrayNode changes = root.putArray("changes");
        for (ConfigChange change : result.getChanges()) {
            ObjectNode node = changes.addObject();
            node.put("type", change.getType().name());
            node.put("path", change.getPath().toString());
            if (change.getLeft() == null) {
                node.putNull("left");
            } else {
                node.set("left", change.getLeft().toJson());
            }
            if (change.getRight() == null) {
                node.putNull("right");
            } else {
                node.set("right", change.getRight().toJson());
            }
            putDisplay(node, "leftDisplay", change.getLeftDisplay());
            putDisplay(node, "rightDisplay", change.getRightDisplay());
            ObjectNode context = node.putObject("context");
            for (Map.Entry<NodePath, String> entry : change.getContext().entrySet()) {
                context.put(entry.getKey().toString(), entry.getValue());
            }
        }

        try {
            out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to write JSON report", e);
        }
    }

    private void putDisplay(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }
}
