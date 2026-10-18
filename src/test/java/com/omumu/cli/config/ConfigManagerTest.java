package com.omumu.cli.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {

    @Test
    void theSavedConfigIsReadableOnlyByItsOwner(@TempDir Path home) throws Exception {
        Path dir = home.resolve(".omumu");
        ConfigManager manager = new ConfigManager(dir);

        manager.save(new OmumuConfig());

        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(dir.resolve("config.json")));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE),
                Files.getPosixFilePermissions(dir));
    }
}
