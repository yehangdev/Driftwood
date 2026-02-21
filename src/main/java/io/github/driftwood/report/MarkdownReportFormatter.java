package io.github.driftwood.report;

import io.github.driftwood.compare.ComparisonResult;
import io.github.driftwood.compare.ConfigChange;
import io.github.driftwood.model.NodePath;

import java.io.PrintWriter;
import java.util.Map;

public final class MarkdownReportFormatter implements ReportFormatter {

    @Override
    public void write(ComparisonResult result, PrintWriter out) {
        out.println("# Configuration diff");
        out.println();
        if (result.isEqual()) {
            out.println("No configuration differences.");
        } else {
            out.println("| Type | Path | Left | Right |");
            out.println("| --- | --- | --- | --- |");
            for (ConfigChange change : result.getChanges()) {
                out.println("| " + change.getType().name().toLowerCase(java.util.Locale.ROOT)
                        + " | `" + escape(change.getPath().toString()) + "`"
                        + " | `" + escape(display(change.getLeftDisplay())) + "`"
                        + " | `" + escape(display(change.getRightDisplay())) + "` |");
            }
            writeContext(out, result);
        }
        out.println();
        out.println("Summary: "
                + result.count(ConfigChange.Type.ADDED) + " added, "
                + result.count(ConfigChange.Type.REMOVED) + " removed, "
                + result.count(ConfigChange.Type.CHANGED) + " changed, "
                + result.getIgnoredCount() + " ignored.");
    }

    private void writeContext(PrintWriter out, ComparisonResult result) {
        boolean hasContext = result.getChanges().stream()
                .anyMatch(change -> !change.getContext().isEmpty());
        if (!hasContext) {
            return;
        }
        out.println();
        out.println("## Context");
        for (ConfigChange change : result.getChanges()) {
            if (change.getContext().isEmpty()) {
                continue;
            }
            out.println();
            out.println("### `" + escape(change.getPath().toString()) + "`");
            for (Map.Entry<NodePath, String> entry : change.getContext().entrySet()) {
                out.println("- `" + escape(entry.getKey().toString()) + " = "
                        + escape(entry.getValue()) + "`");
            }
        }
    }

    private String display(String value) {
        return value == null ? "" : value;
    }

    private String escape(String value) {
        return value.replace("|", "\\|").replace("`", "\\`");
    }
}
