package io.github.driftwood.report;

public enum ReportFormat {
    TEXT,
    JSON,
    MARKDOWN;

    public static ReportFormat fromName(String value) {
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("report format must be text, json, or markdown: " + value);
        }
    }
}
