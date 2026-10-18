package com.omumu.cli.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ConfigManagerTest {

    private static final Set<PosixFilePermission> OWNER_RW = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_RWX = PosixFilePermissions.fromString("rwx------");

    @BeforeAll
    static void posixOnly() {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    }

    @Test
    void theSavedConfigIsReadableOnlyByItsOwner(@TempDir Path home) throws Exception {
        Path dir = home.resolve(".omumu");
        ConfigManager manager = new ConfigManager(dir);

        manager.save(new OmumuConfig());

        assertEquals(OWNER_RW, Files.getPosixFilePermissions(dir.resolve("config.json")));
        assertEquals(OWNER_RWX, Files.getPosixFilePermissions(dir));
        assertEquals(1, Files.list(dir).count(), "no temp file left behind");
    }

    @Test
    void loadingTightensAConfigFileAnOlderVersionLeftWorldReadable(@TempDir Path home) throws Exception {
        Path dir = home.resolve(".omumu");
        Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxr-xr-x")));
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{}");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
        ConfigManager manager = new ConfigManager(dir);

        manager.load();

        assertEquals(OWNER_RW, Files.getPosixFilePermissions(file));
        assertEquals(OWNER_RWX, Files.getPosixFilePermissions(dir));
    }
}
