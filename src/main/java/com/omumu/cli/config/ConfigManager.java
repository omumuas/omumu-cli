package com.omumu.cli.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class ConfigManager {

    private static final Path DEFAULT_CONFIG_DIR = Path.of(System.getProperty("user.home"), ".omumu");

    private static final Set<PosixFilePermission> OWNER_ONLY_DIR = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_ONLY_FILE = PosixFilePermissions.fromString("rw-------");

    private final Path configDir;
    private final Path configFile;
    private final ObjectMapper mapper;

    public ConfigManager() {
        this(DEFAULT_CONFIG_DIR);
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
        tightenPermissions();
        try {
            return mapper.readValue(configFile.toFile(), OmumuConfig.class);
        } catch (IOException e) {
            System.err.println("Warning: Could not read config file: " + e.getMessage());
            return new OmumuConfig();
        }
    }

    /**
     * Writes the config. The file holds login tokens, so it and its directory are readable by the owner
     * only, from the first byte: the directory is created owner-only, the content goes to an owner-only
     * temp file, and that file is moved into place atomically.
     */
    public void save(OmumuConfig config) throws IOException {
        if (!posix()) {
            Files.createDirectories(configDir);
            mapper.writeValue(configFile.toFile(), config);
            return;
        }
        Files.createDirectories(configDir, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIR));
        Files.setPosixFilePermissions(configDir, OWNER_ONLY_DIR);
        Path tmp = Files.createTempFile(configDir, "config", ".tmp",
                PosixFilePermissions.asFileAttribute(OWNER_ONLY_FILE));
        try {
            mapper.writeValue(tmp.toFile(), config);
            Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** A config written by an earlier version may be world-readable; make it owner-only on first contact. */
    private void tightenPermissions() {
        if (!posix()) {
            return;
        }
        try {
            Files.setPosixFilePermissions(configDir, OWNER_ONLY_DIR);
            Files.setPosixFilePermissions(configFile, OWNER_ONLY_FILE);
        } catch (IOException e) {
            System.err.println("Warning: Could not restrict the config file's permissions: " + e.getMessage());
        }
    }

    private static boolean posix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    public Path getConfigFile() {
        return configFile;
    }

    public boolean exists() {
        return Files.exists(configFile);
    }
}
