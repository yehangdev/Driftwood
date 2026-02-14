package io.github.driftwood.report;

import io.github.driftwood.compare.ComparisonResult;

import java.io.PrintWriter;

public interface ReportFormatter {

    void write(ComparisonResult result, PrintWriter out);
}
