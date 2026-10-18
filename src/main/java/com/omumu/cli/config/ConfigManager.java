package com.omumu.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

public class ConfigManager {

    private static final Path DEFAULT_configDir = Path.of(System.getProperty("user.home"), ".omumu");

    private final Path configDir;
    private final Path configFile;
    private final ObjectMapper mapper;

    public ConfigManager() {
        this(DEFAULT_configDir);
    }

    /** The config lives in {@code configDir/config.json}; tests point this at a scratch directory. */
    ConfigManager(Path configDir) {
        this.configDir = configDir;
        this.configFile = configDir.resolve("config.json");
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public OmumuConfig load() {
        if (!Files.exists(configFile)) {
            return new OmumuConfig();
        }
        try {
            return mapper.readValue(configFile.toFile(), OmumuConfig.class);
        } catch (IOException e) {
            System.err.println("Warning: Could not read config file: " + e.getMessage());
            return new OmumuConfig();
        }
    }

    /** Writes the config; the file holds login tokens, so it and its directory are readable by the owner only. */
    public void save(OmumuConfig config) throws IOException {
        Files.createDirectories(configDir);
        mapper.writeValue(configFile.toFile(), config);
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(configDir, PosixFilePermissions.fromString("rwx------"));
            Files.setPosixFilePermissions(configFile, PosixFilePermissions.fromString("rw-------"));
        }
    }

    public Path getConfigFile() {
        return configFile;
    }

    public boolean exists() {
        return Files.exists(configFile);
    }
}
