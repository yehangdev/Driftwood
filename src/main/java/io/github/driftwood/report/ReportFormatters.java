package io.github.driftwood.report;

public final class ReportFormatters {

    private ReportFormatters() {
    }

    public static ReportFormatter create(ReportFormat format) {
        switch (format) {
            case TEXT:
                return new TextReportFormatter();
            case JSON:
                return new JsonReportFormatter();
            case MARKDOWN:
                return new MarkdownReportFormatter();
            default:
                throw new IllegalArgumentException("unsupported report format: " + format);
        }
    }
}
