package io.github.driftwood;

import io.github.driftwood.cli.DriftwoodCommand;
import picocli.CommandLine;

public final class DriftwoodApplication {

    private DriftwoodApplication() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DriftwoodCommand())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }
}
