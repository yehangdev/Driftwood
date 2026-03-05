package io.github.driftwood.cli;

import io.github.driftwood.compare.ArrayOrder;
import io.github.driftwood.compare.ComparisonOptions;
import io.github.driftwood.compare.ComparisonRequest;
import io.github.driftwood.compare.ComparisonResult;
import io.github.driftwood.compare.ConfigComparisonEngine;
import io.github.driftwood.core.ConfigDiffer;
import io.github.driftwood.core.ConfigException;
import io.github.driftwood.core.ConfigFormat;
import io.github.driftwood.core.ConfigLoader;
import io.github.driftwood.model.NodePath;
import io.github.driftwood.report.ReportFormat;
import io.github.driftwood.report.ReportFormatter;
import io.github.driftwood.report.ReportFormatters;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "driftwood",
        mixinStandardHelpOptions = true,
        version = "1.1.0",
        description = "Compare application configuration files across environments.")
public final class DriftwoodCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-l", "--left"},
            required = true,
            paramLabel = "FILE",
            description = "Left configuration file.")
    private Path left;

    @Option(
            names = {"-r", "--right"},
            required = true,
            paramLabel = "FILE",
            description = "Right configuration file.")
    private Path right;

    @Option(
            names = "--left-format",
            defaultValue = "AUTO",
            paramLabel = "FORMAT",
            description = "Left input format: AUTO, PROPERTIES, YAML, JSON, or ENV "
                    + "(default: ${DEFAULT-VALUE}).")
    private ConfigFormat leftFormat;

    @Option(
            names = "--right-format",
            defaultValue = "AUTO",
            paramLabel = "FORMAT",
            description = "Right input format: AUTO, PROPERTIES, YAML, JSON, or ENV "
                    + "(default: ${DEFAULT-VALUE}).")
    private ConfigFormat rightFormat;

    @Option(
            names = "--left-overlay",
            paramLabel = "FILE",
            description = "Overlay merged over the left input before comparison.")
    private Path leftOverlay;

    @Option(
            names = "--right-overlay",
            paramLabel = "FILE",
            description = "Overlay merged over the right input before comparison.")
    private Path rightOverlay;

    @Option(
            names = "--left-overlay-format",
            defaultValue = "AUTO",
            paramLabel = "FORMAT",
            description = "Left overlay format (default: ${DEFAULT-VALUE}).")
    private ConfigFormat leftOverlayFormat;

    @Option(
            names = "--right-overlay-format",
            defaultValue = "AUTO",
            paramLabel = "FORMAT",
            description = "Right overlay format (default: ${DEFAULT-VALUE}).")
    private ConfigFormat rightOverlayFormat;

    @Option(
            names = "--profile",
            paramLabel = "PATH",
            description = "Compare only this profile subtree, for example dev or profiles.dev.")
    private String profile;

    @Option(
            names = "--include",
            paramLabel = "PATH",
            description = "Compare only this path or descendants. Repeat for multiple paths.")
    private List<String> includes = new ArrayList<>();

    @Option(
            names = "--exclude",
            paramLabel = "PATH",
            description = "Skip this path and its descendants. Repeat for multiple paths.")
    private List<String> excludes = new ArrayList<>();

    @Option(
            names = "--rules",
            paramLabel = "FILE",
            description = "Rule file containing ignore, mask, ordered, or unordered directives.")
    private Path rulesFile;

    @Option(
            names = "--ignore",
            paramLabel = "PATH",
            description = "Ignore a path or subtree. Repeat for multiple paths.")
    private List<String> ignores = new ArrayList<>();

    @Option(
            names = "--mask",
            paramLabel = "PATH",
            description = "Mask displayed values at a path. Repeat for multiple paths.")
    private List<String> masks = new ArrayList<>();

    @Option(
            names = "--mask-replacement",
            defaultValue = "<redacted>",
            paramLabel = "TEXT",
            description = "Replacement shown for masked values (default: ${DEFAULT-VALUE}).")
    private String maskReplacement;

    @Option(
            names = "--array-order",
            defaultValue = "ORDERED",
            paramLabel = "ORDER",
            description = "Default array strategy: ORDERED or UNORDERED "
                    + "(default: ${DEFAULT-VALUE}).")
    private ArrayOrder arrayOrder;

    @Option(
            names = "--ordered-path",
            paramLabel = "PATH",
            description = "Treat matching arrays as ordered. Repeat for multiple paths.")
    private List<String> orderedPaths = new ArrayList<>();

    @Option(
            names = "--unordered-path",
            paramLabel = "PATH",
            description = "Treat matching arrays as unordered. Repeat for multiple paths.")
    private List<String> unorderedPaths = new ArrayList<>();

    @Option(
            names = "--context",
            defaultValue = "0",
            paramLabel = "N",
            description = "Include up to N nearby siblings for each change (default: ${DEFAULT-VALUE}).")
    private int contextLines;

    @Option(
            names = {"-f", "--format"},
            defaultValue = "TEXT",
            paramLabel = "FORMAT",
            description = "Report format: TEXT, JSON, or MARKDOWN (default: ${DEFAULT-VALUE}).")
    private ReportFormat reportFormat;

    @Option(
            names = {"-o", "--output"},
            paramLabel = "FILE",
            description = "Write the report to a file instead of standard output.")
    private Path output;

    @Option(
            names = "--schema",
            paramLabel = "FILE",
            description = "Validate required paths, types, and array order declarations.")
    private Path schemaFile;

    @Option(
            names = "--validate",
            description = "Validate inputs, overlays, rules, and schema, then exit without comparing.")
    private boolean validateOnly;

    @Option(
            names = "--fail-on-ignored",
            description = "Return exit code 1 when differences exist only in ignored paths.")
    private boolean failOnIgnored;

    @Option(
            names = "--redact",
            defaultValue = "true",
            description = "Mask values for sensitive keys by default (default: ${DEFAULT-VALUE}).")
    private boolean redact;

    @Option(
            names = "--no-redact",
            description = "Do not mask values for sensitive keys.")
    private boolean noRedact;

    private final ConfigComparisonEngine engine;

    public DriftwoodCommand() {
        this(new ConfigComparisonEngine());
    }

    DriftwoodCommand(ConfigComparisonEngine engine) {
        this.engine = engine;
    }

    /**
     * Kept for source compatibility with callers that constructed the original command with its
     * two parser collaborators. The expanded engine owns those responsibilities now.
     */
    DriftwoodCommand(ConfigLoader loader, ConfigDiffer differ) {
        this();
    }

    @Override
    public Integer call() {
        try {
            ComparisonRequest request = buildRequest();
            if (validateOnly) {
                engine.validate(request);
                spec.commandLine().getOut().println("Validation succeeded.");
                return 0;
            }

            ComparisonResult result = engine.compare(request);
            writeReport(result);
            if (!result.getChanges().isEmpty()) {
                return 1;
            }
            if (failOnIgnored && result.getIgnoredCount() > 0) {
                return 1;
            }
            return 0;
        } catch (ConfigException | IllegalArgumentException e) {
            spec.commandLine().getErr().println("error: " + e.getMessage());
            return 2;
        } catch (RuntimeException e) {
            spec.commandLine().getErr().println("error: unexpected failure: " + e.getMessage());
            return 3;
        }
    }

    private ComparisonRequest buildRequest() {
        ComparisonOptions options = ComparisonOptions.builder()
                .defaultArrayOrder(arrayOrder)
                .contextLines(contextLines)
                .failOnIgnored(failOnIgnored)
                .build();
        for (String include : includes) {
            options = addInclude(options, include);
        }
        for (String exclude : excludes) {
            options = addExclude(options, exclude);
        }

        ComparisonRequest.Builder builder = ComparisonRequest.builder()
                .left(left)
                .right(right)
                .leftFormat(leftFormat)
                .rightFormat(rightFormat)
                .leftOverlay(leftOverlay)
                .rightOverlay(rightOverlay)
                .leftOverlayFormat(leftOverlayFormat)
                .rightOverlayFormat(rightOverlayFormat)
                .profile(profile)
                .rulesFile(rulesFile)
                .schemaFile(schemaFile)
                .options(options)
                .sensitiveMasking(redact && !noRedact)
                .maskReplacement(maskReplacement);
        for (String ignore : ignores) {
            builder.ignore(ignore);
        }
        for (String mask : masks) {
            builder.mask(mask);
        }
        for (String path : orderedPaths) {
            builder.orderedPath(path);
        }
        for (String path : unorderedPaths) {
            builder.unorderedPath(path);
        }
        return builder.build();
    }

    private ComparisonOptions addInclude(ComparisonOptions current, String path) {
        ComparisonOptions.Builder builder = copyOptions(current);
        return builder.include(path).build();
    }

    private ComparisonOptions addExclude(ComparisonOptions current, String path) {
        ComparisonOptions.Builder builder = copyOptions(current);
        return builder.exclude(path).build();
    }

    private ComparisonOptions.Builder copyOptions(ComparisonOptions current) {
        ComparisonOptions.Builder builder = ComparisonOptions.builder()
                .defaultArrayOrder(current.getDefaultArrayOrder())
                .contextLines(current.getContextLines())
                .failOnIgnored(current.isFailOnIgnored());
        for (NodePath include : current.getIncludes()) {
            builder.include(include);
        }
        for (NodePath exclude : current.getExcludes()) {
            builder.exclude(exclude);
        }
        return builder;
    }

    private void writeReport(ComparisonResult result) throws ConfigException {
        ReportFormatter formatter = ReportFormatters.create(reportFormat);
        if (output == null) {
            formatter.write(result, spec.commandLine().getOut());
            return;
        }

        Path parent = output.toAbsolutePath().getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            throw new ConfigException("report output directory does not exist: " + parent);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            PrintWriter reportWriter = new PrintWriter(writer);
            formatter.write(result, reportWriter);
            reportWriter.flush();
        } catch (IOException e) {
            throw new ConfigException("failed to write report '" + output + "': " + e.getMessage(), e);
        }
    }
}
