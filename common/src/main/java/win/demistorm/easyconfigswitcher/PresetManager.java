package win.demistorm.easyconfigswitcher;

import win.demistorm.easyconfigswitcher.config.ModConfig;
import win.demistorm.easyconfigswitcher.config.Preset;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PresetManager {

    private static final int MAX_PRESETS = 10;
    private static final int MAX_NAME_LENGTH = 15;
    private static final Path GAME = Path.of("");

    private PresetManager() {
    }

    public static String createPreset(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "Preset name cannot be empty";
        }

        name = name.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            return "Preset name must be " + MAX_NAME_LENGTH + " characters or less";
        }

        if (getPreset(name).isPresent()) {
            return "A preset with this name already exists";
        }

        if (ModConfig.INSTANCE.presets.size() >= MAX_PRESETS) {
            return "Maximum number of presets (" + MAX_PRESETS + ") reached";
        }

        boolean firstEver = ModConfig.INSTANCE.presets.isEmpty();
        String baseName = ModConfig.getBasePresetName();
        boolean baseAvailable = baseName != null && ConfigBackupManager.presetExists(baseName);

        Preset preset = new Preset(name, ModConfig.INSTANCE.presets.size());

        try {
            if (firstEver || !baseAvailable) {
                PresetStore.captureFullTo(GAME, PresetStore.presetDir(GAME, name));
            } else {
                PresetStore.captureDerivedFromLive(GAME, name, baseName);
            }
        } catch (IOException e) {
            EasyConfigSwitcher.LOGGER.error("Failed to create preset '{}'", name, e);
            return "Failed to create preset backup";
        }

        ModConfig.INSTANCE.presets.add(preset);
        if (firstEver) {
            ModConfig.setBasePresetName(name);
        }
        ModConfig.save();

        boolean isBase = name.equals(ModConfig.getBasePresetName());
        EasyConfigSwitcher.LOGGER.info("Created preset '{}'{}", name, isBase ? " (base)" : "");
        return "Preset '" + name + "' created successfully" + (isBase ? " (Base: others will inherit from it)" : "");
    }

    public static String setBase(String name) {
        if (getPreset(name).isEmpty()) {
            return "Preset not found: " + name;
        }
        if (name.equals(ModConfig.getBasePresetName())) {
            return "'" + name + "' is already the base preset";
        }

        List<String> others = new ArrayList<>();
        for (Preset preset : ModConfig.INSTANCE.presets) {
            if (!preset.getName().equals(name)) {
                others.add(preset.getName());
            }
        }

        try {
            PresetStore.promoteToBase(GAME, name, others);
        } catch (IOException e) {
            EasyConfigSwitcher.LOGGER.error("Failed to set '{}' as base preset", name, e);
            return "Failed to set base preset";
        }

        ModConfig.setBasePresetName(name);
        EasyConfigSwitcher.LOGGER.info("Set '{}' as base preset, {} presets now derive from it", name, others.size());
        return "Preset '" + name + "' is now the base successfully (" + others.size() + " preset" + (others.size() == 1 ? "" : "s") + " inherit from it)";
    }

    public static String updatePreset(String name) {
        Optional<Preset> presetOpt = getPreset(name);
        if (presetOpt.isEmpty()) {
            return "Preset not found: " + name;
        }
        if (!ConfigBackupManager.presetExists(name)) {
            return "Preset backup not found: " + name;
        }

        String baseName = ModConfig.getBasePresetName();
        boolean isBase = name.equals(baseName);
        boolean isDerived = !isBase && PresetStore.isDerived(GAME, name);

        if (!isBase && !isDerived) {
            try {
                PresetStore.captureFullTo(GAME, PresetStore.presetDir(GAME, name));
            } catch (IOException e) {
                EasyConfigSwitcher.LOGGER.error("Failed to update preset '{}'", name, e);
                return "Failed to update preset";
            }
            return "Preset '" + name + "' updated successfully (full recapture)";
        }

        Path referenceDir = null;
        Path targetDir = null;
        String current = ModConfig.getCurrentPreset();
        if (current != null && ConfigBackupManager.presetExists(current)) {
            try {
                referenceDir = PresetStore.stagingDir(GAME).resolve("ref");
                PresetStore.applyTo(GAME, current, referenceDir);
            } catch (IOException e) {
                EasyConfigSwitcher.LOGGER.error("Failed to stage current preset '{}' for update", current, e);
                referenceDir = null;
            }
        }
        if (isDerived && !name.equals(current)) {
            try {
                targetDir = PresetStore.stagingDir(GAME).resolve("ref-target");
                PresetStore.applyTo(GAME, name, targetDir);
            } catch (IOException e) {
                EasyConfigSwitcher.LOGGER.error("Failed to stage target preset '{}' for update", name, e);
                targetDir = null;
            }
        }

        try {
            PresetStore.ChangeSet changes = PresetStore.computeSessionChanges(GAME, referenceDir);
            if (changes.isEmpty()) {
                return "No changes detected for preset '" + name + "'";
            }

            if (isBase) {
                PresetStore.updateBase(GAME, name, changes);
                for (Preset preset : ModConfig.INSTANCE.presets) {
                    if (!preset.getName().equals(name) && PresetStore.isDerived(GAME, preset.getName())) {
                        PresetStore.pruneRedundantOverrides(GAME, preset.getName());
                    }
                }
            } else {
                PresetStore.updateDerived(GAME, name, changes, referenceDir, targetDir);
            }

            if (referenceDir != null) {
                PresetStore.deleteTreeQuietly(referenceDir);
            }
            if (targetDir != null) {
                PresetStore.deleteTreeQuietly(targetDir);
            }

            EasyConfigSwitcher.LOGGER.info("Updated preset '{}': {}", name, changes.summary());
            return "Preset '" + name + "' updated successfully (" + changes.summary() + ")";
        } catch (IOException e) {
            EasyConfigSwitcher.LOGGER.error("Failed to update preset '{}'", name, e);
            return "Failed to update preset";
        }
    }

    public static String deletePreset(String name) {
        Optional<Preset> presetOpt = getPreset(name);
        if (presetOpt.isEmpty()) {
            return "Preset not found: " + name;
        }

        if (name.equals(ModConfig.getBasePresetName())) {
            List<String> others = new ArrayList<>();
            for (Preset preset : ModConfig.INSTANCE.presets) {
                if (!preset.getName().equals(name)) {
                    others.add(preset.getName());
                }
            }
            if (!others.isEmpty()) {
                try {
                    PresetStore.demoteBase(GAME, others);
                } catch (IOException e) {
                    EasyConfigSwitcher.LOGGER.error("Failed to convert derived presets to standalone while deleting base '{}'", name, e);
                    return "Failed to delete base preset";
                }
            }
            ModConfig.setBasePresetName(null);
        }

        ConfigBackupManager.deletePreset(name);

        Preset preset = presetOpt.get();
        ModConfig.INSTANCE.presets.remove(preset);
        reorderPresetsAfterDelete(preset.getOrder());
        ModConfig.save();

        EasyConfigSwitcher.LOGGER.info("Deleted preset: {}", name);
        return "Preset '" + name + "' deleted";
    }

    public static String applyOnRestart(String name) {
        if (!ConfigBackupManager.presetExists(name)) {
            return "Preset backup not found: " + name;
        }

        ModConfig.setPendingPreset(name);

        EasyConfigSwitcher.LOGGER.info("Preset '{}' scheduled for next startup. Shutting down...", name);

        return "SHUTDOWN:Preset '" + name + "' will be applied. Game shutting down...";
    }

    public static void reorderPresets(List<String> newOrder) {
        if (newOrder.size() != ModConfig.INSTANCE.presets.size()) {
            return;
        }

        List<Preset> reordered = new ArrayList<>();
        for (int i = 0; i < newOrder.size(); i++) {
            String name = newOrder.get(i);
            Optional<Preset> presetOpt = getPreset(name);
            if (presetOpt.isEmpty()) {
                return;
            }
            Preset preset = presetOpt.get();
            preset.setOrder(i);
            reordered.add(preset);
        }

        ModConfig.INSTANCE.presets.clear();
        ModConfig.INSTANCE.presets.addAll(reordered);
        ModConfig.save();

        EasyConfigSwitcher.LOGGER.info("Reordered presets");
    }

    public static List<Preset> getAllPresets() {
        return ModConfig.INSTANCE.presets.stream()
                .sorted(Comparator.comparingInt(Preset::getOrder))
                .toList();
    }

    public static Optional<Preset> getPreset(String name) {
        return ModConfig.INSTANCE.presets.stream()
                .filter(p -> p.getName().equals(name))
                .findFirst();
    }

    public static boolean isBase(String name) {
        return name != null && name.equals(ModConfig.getBasePresetName());
    }

    public static int getMaxPresets() {
        return MAX_PRESETS;
    }

    public static int getMaxNameLength() {
        return MAX_NAME_LENGTH;
    }

    private static void reorderPresetsAfterDelete(int deletedOrder) {
        for (Preset preset : ModConfig.INSTANCE.presets) {
            if (preset.getOrder() > deletedOrder) {
                preset.setOrder(preset.getOrder() - 1);
            }
        }
    }
}
