package win.demistorm.easyconfigswitcher;

import win.demistorm.easyconfigswitcher.config.ModConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

public final class ShutdownHookManager {

    private static volatile boolean registered = false;

    private ShutdownHookManager() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ModConfig.load();
                String pendingPreset = ModConfig.INSTANCE.pendingPreset;

                if (pendingPreset != null && !pendingPreset.isEmpty()) {
                    hookLog("Applying pending preset: " + pendingPreset);

                    boolean ok = false;
                    if (ConfigBackupManager.presetExists(pendingPreset)) {
                        ok = applyPreset(pendingPreset);
                    } else {
                        hookLog("ERROR: Preset '" + pendingPreset + "' not found on disk");
                    }

                    if (ok) {
                        ModConfig.setCurrentPreset(pendingPreset);
                        hookLog("Preset '" + pendingPreset + "' applied successfully");
                    } else {
                        hookLog("ERROR: Failed to apply preset '" + pendingPreset + "'");
                    }

                    ModConfig.clearPendingPreset();
                }
            } catch (Exception e) {
                System.err.println("[ShutdownHook] Error applying preset: " + e.getMessage());
                e.printStackTrace();
            }
        }, "ecs-preset-swap"));
    }

    private static void hookLog(String message) {
        EasyConfigSwitcher.LOGGER.info("[ShutdownHook] {}", message);
        System.err.println("[ECS ShutdownHook] " + message);
    }

    private static boolean applyPreset(String presetName) {
        return applyPreset(Path.of(""), presetName, ConfigBackupManager::restoreFrom);
    }

    static boolean applyPreset(Path game, String presetName, Function<Path, Boolean> restorer) {
        try {
            PresetStore.captureFullTo(game, PresetStore.preRestoreDir(game));
        } catch (Exception e) {
            EasyConfigSwitcher.LOGGER.error("[ShutdownHook] Safety snapshot failed, aborting preset apply", e);
            return false;
        }

        try {
            Path sourceDir = PresetStore.presetDir(game, presetName);
            boolean staged = false;
            if (PresetStore.isDerived(game, presetName)) {
                Path staging = PresetStore.stagingDir(game).resolve(presetName);
                PresetStore.MergeStats stats = PresetStore.applyWithStats(game, presetName, staging);
                EasyConfigSwitcher.LOGGER.info("[ShutdownHook] Applied preset '{}' ({}), conflicts resolved in preset's favor: {}",
                        presetName, stats, stats.conflicts);
                System.err.println("[ECS ShutdownHook] Applied preset '" + presetName + "' (" + stats
                        + "), conflicts resolved in preset's favor: " + stats.conflicts);
                sourceDir = staging;
                staged = true;
            }
            boolean restored = restorer.apply(sourceDir);
            if (staged) {
                PresetStore.deleteTreeQuietly(PresetStore.stagingDir(game));
            }
            return restored;
        } catch (Exception e) {
            EasyConfigSwitcher.LOGGER.error("[ShutdownHook] Error applying preset '{}'", presetName, e);
            return false;
        }
    }
}
