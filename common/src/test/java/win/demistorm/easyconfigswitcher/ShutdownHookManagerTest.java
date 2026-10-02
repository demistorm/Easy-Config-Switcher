package win.demistorm.easyconfigswitcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutdownHookManagerTest {

    @TempDir
    Path game;

    private final Queue<Path> restoredFrom = new ArrayDeque<>();

    @BeforeEach
    void setUpPresets() throws IOException {
        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:12
                guiScale:3
                resourcePacks:["vanilla"]
                """);
        write(game.resolve("config/sodium.json"), "{\n\"a\":1,\n\"b\":2\n}\n");

        PresetStore.captureFullTo(game, PresetStore.presetDir(game, "Base"));

        write(game.resolve("options.txt"), """
                version:4120
                renderDistance:9
                guiScale:3
                resourcePacks:["vanilla"]
                """);
        PresetStore.captureDerivedFromLive(game, "Performance", "Base");
    }

    @Test
    void applyOfBasePresetKeepsAllPresetDirs() throws IOException {
        boolean ok = ShutdownHookManager.applyPreset(game, "Base", this::recordRestore);

        assertTrue(ok);
        assertEquals(PresetStore.presetDir(game, "Base"), restoredFrom.poll());
        assertTrue(Files.exists(PresetStore.presetDir(game, "Base").resolve("options.txt")));
        assertTrue(Files.exists(PresetStore.presetDir(game, "Performance").resolve("delta.json")));
        assertTrue(Files.exists(PresetStore.preRestoreDir(game).resolve("options.txt")));
        assertFalse(Files.exists(PresetStore.stagingDir(game).resolve("Base")));
    }

    @Test
    void applyOfDerivedPresetRestoresFromStagingAndCleansOnlyStaging() throws IOException {
        boolean ok = ShutdownHookManager.applyPreset(game, "Performance", this::recordRestore);

        assertTrue(ok);
        assertEquals(PresetStore.stagingDir(game).resolve("Performance"), restoredFrom.poll());
        assertFalse(Files.exists(PresetStore.stagingDir(game)));
        assertTrue(Files.exists(PresetStore.presetDir(game, "Performance").resolve("delta.json")));
        assertTrue(Files.exists(PresetStore.presetDir(game, "Base").resolve("options.txt")));
    }

    private boolean recordRestore(Path sourceDir) {
        restoredFrom.add(sourceDir);
        return true;
    }

    private void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
