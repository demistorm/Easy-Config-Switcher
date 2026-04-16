package win.demistorm.easyconfigswitcher;

import win.demistorm.easyconfigswitcher.config.ModConfig;
import win.demistorm.easyconfigswitcher.config.Preset;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PresetManager {

    private static final int MAX_PRESETS = 10;
    private static final int MAX_NAME_LENGTH = 15;

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

        int order = ModConfig.INSTANCE.presets.size();

        Preset preset = new Preset(name, order);

        if (!ConfigBackupManager.backupPreset(name)) {
            return "Failed to create preset backup";
        }

        ModConfig.INSTANCE.presets.add(preset);
        ModConfig.save();

        EasyConfigSwitcher.LOGGER.info("Created preset: {}", name);
        return "Preset '" + name + "' created successfully";
    }

    public static String deletePreset(String name) {
        Optional<Preset> presetOpt = getPreset(name);
        if (presetOpt.isEmpty()) {
            return "Preset not found: " + name;
        }

        Preset preset = presetOpt.get();

        ConfigBackupManager.deletePreset(name);

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
