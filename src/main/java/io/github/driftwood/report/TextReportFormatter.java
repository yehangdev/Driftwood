package io.github.driftwood.report;

import io.github.driftwood.compare.ComparisonResult;
import io.github.driftwood.compare.ConfigChange;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TextReportFormatter implements ReportFormatter {

    @Override
    public void write(ComparisonResult result, PrintWriter out) {
        if (result.getChanges().isEmpty()) {
            out.println("No configuration differences.");
            if (result.getIgnoredCount() > 0) {
                out.println("Ignored changes (" + result.getIgnoredCount() + ").");
            }
            return;
        }

        writeSection(out, "Added keys", ConfigChange.Type.ADDED, result);
        writeSection(out, "Removed keys", ConfigChange.Type.REMOVED, result);
        writeSection(out, "Changed keys", ConfigChange.Type.CHANGED, result);
        if (result.getIgnoredCount() > 0) {
            out.println("Ignored changes (" + result.getIgnoredCount() + ").");
        }
    }

    private void writeSection(
            PrintWriter out,
            String heading,
            ConfigChange.Type type,
            ComparisonResult result) {
        List<ConfigChange> selected = result.getChanges().stream()
                .filter(change -> change.getType() == type)
                .collect(Collectors.toList());
        if (selected.isEmpty()) {
            return;
        }
        out.println(heading + " (" + selected.size() + "):");
        for (ConfigChange change : selected) {
            out.println("  " + marker(type) + " " + renderChange(change));
            writeContext(out, change);
        }
        out.println();
    }

    private String renderChange(ConfigChange change) {
        switch (change.getType()) {
            case ADDED:
                return change.getPath().toString();
            case REMOVED:
                return change.getPath().toString();
            case CHANGED:
                return change.getPath() + ": "
                        + change.getLeftDisplay() + " -> " + change.getRightDisplay();
            default:
                throw new IllegalStateException("unexpected change type");
        }
    }

    private void writeContext(PrintWriter out, ConfigChange change) {
        for (Map.Entry<io.github.driftwood.model.NodePath, String> entry
                : change.getContext().entrySet()) {
            out.println("    context: " + entry.getKey() + " = " + entry.getValue());
        }
    }

    private String marker(ConfigChange.Type type) {
        switch (type) {
            case ADDED:
                return "+";
            case REMOVED:
                return "-";
            case CHANGED:
                return "~";
            default:
                throw new IllegalStateException("unexpected diff type");
        }
    }
}
