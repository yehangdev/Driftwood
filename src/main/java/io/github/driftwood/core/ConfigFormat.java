package io.github.driftwood.core;

import java.nio.file.Path;
import java.util.Locale;

public enum ConfigFormat {
    AUTO,
    PROPERTIES,
    YAML,
    JSON,
    ENV;

    public static ConfigFormat detect(Path path) throws ConfigException {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".properties")) {
            return PROPERTIES;
        }
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            return YAML;
        }
        if (fileName.endsWith(".json")) {
            return JSON;
        }
        if (fileName.endsWith(".env") || fileName.equals(".env")) {
            return ENV;
        }
        throw new ConfigException(
                "cannot determine the format of '" + path + "'; specify the format explicitly");
    }
}
