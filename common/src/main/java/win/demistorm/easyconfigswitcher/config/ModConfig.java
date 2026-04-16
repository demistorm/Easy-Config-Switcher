package win.demistorm.easyconfigswitcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModConfig {

    public static final class Data {
        public List<Preset> presets = new ArrayList<>();
        public String pendingPreset = null;
        public String currentPreset = null;
        public String titleLabel = "Easy Config Switcher";
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ECS_DIR = Path.of("ecs-presets");
    private static final Path CONFIG_FILE = ECS_DIR.resolve("mod-config.json");

    private static final Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("EasyConfigSwitcher");

    public static final Data INSTANCE = new Data();

    private ModConfig() {
    }

    public static void load() {
        Data loaded = read();
        copyInto(loaded);
    }

    public static void save() {
        write();
    }

    private static Data read() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                Data data = GSON.fromJson(json, Data.class);
                if (data == null) {
                    return new Data();
                }
                if (data.presets == null) {
                    data.presets = new ArrayList<>();
                }
                return data;
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read Easy Config Switcher config, using defaults", e);
        }
        return new Data();
    }

    private static void write() {
        try {
            Files.createDirectories(ECS_DIR);
            String json = GSON.toJson(ModConfig.INSTANCE, Data.class);
            Files.writeString(CONFIG_FILE, json);
            LOGGER.debug("Easy Config Switcher config saved");
        } catch (IOException e) {
            LOGGER.error("Failed to write Easy Config Switcher config", e);
        }
    }

    private static void copyInto(Data from) {
        INSTANCE.presets.clear();
        INSTANCE.presets.addAll(from.presets);
        INSTANCE.pendingPreset = from.pendingPreset;
        INSTANCE.currentPreset = from.currentPreset;
        INSTANCE.titleLabel = from.titleLabel != null && !from.titleLabel.isEmpty() ? from.titleLabel : "Easy Config Switcher";
    }

    public static void clearPendingPreset() {
        INSTANCE.pendingPreset = null;
        save();
    }

    public static void setPendingPreset(String presetName) {
        INSTANCE.pendingPreset = presetName;
        save();
    }

    public static String getCurrentPreset() {
        return INSTANCE.currentPreset;
    }

    public static void setCurrentPreset(String presetName) {
        INSTANCE.currentPreset = presetName;
        save();
    }

    public static String getTitleLabel() {
        String label = INSTANCE.titleLabel;
        return label != null && !label.isEmpty() ? label : "Easy Config Switcher";
    }

    public static void setTitleLabel(String label) {
        INSTANCE.titleLabel = label;
        save();
    }
}
