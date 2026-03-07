package com.omumu.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ConfigManager {

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".omumu");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    private final ObjectMapper mapper;

    public ConfigManager() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public OmumuConfig load() {
        if (!Files.exists(CONFIG_FILE)) {
            return new OmumuConfig();
        }
        try {
            return mapper.readValue(CONFIG_FILE.toFile(), OmumuConfig.class);
        } catch (IOException e) {
            System.err.println("Warning: Could not read config file: " + e.getMessage());
            return new OmumuConfig();
        }
    }

    public void save(OmumuConfig config) throws IOException {
        Files.createDirectories(CONFIG_DIR);
        mapper.writeValue(CONFIG_FILE.toFile(), config);
    }

    public Path getConfigFile() {
        return CONFIG_FILE;
    }

    public boolean exists() {
        return Files.exists(CONFIG_FILE);
    }
}
