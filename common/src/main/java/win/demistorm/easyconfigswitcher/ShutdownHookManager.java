package win.demistorm.easyconfigswitcher;

import win.demistorm.easyconfigswitcher.config.ModConfig;

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
                    EasyConfigSwitcher.LOGGER.info("[ShutdownHook] Applying pending preset: {}", pendingPreset);

                    if (ConfigBackupManager.restorePreset(pendingPreset)) {
                        ModConfig.setCurrentPreset(pendingPreset);
                        EasyConfigSwitcher.LOGGER.info("[ShutdownHook] Preset '{}' applied successfully", pendingPreset);
                    } else {
                        EasyConfigSwitcher.LOGGER.error("[ShutdownHook] Failed to apply preset '{}'", pendingPreset);
                    }

                    ModConfig.clearPendingPreset();
                }
            } catch (Exception e) {
                System.err.println("[ShutdownHook] Error applying preset: " + e.getMessage());
                e.printStackTrace();
            }
        }, "ecs-preset-swap"));
    }
}
