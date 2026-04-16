package win.demistorm.easyconfigswitcher.fabric.client;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.fabricmc.api.ClientModInitializer;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;
import win.demistorm.easyconfigswitcher.config.ModConfig;
import win.demistorm.easyconfigswitcher.client.ConfigScreen;

public class FabricClientSetup implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EasyConfigSwitcher.LOGGER.info("Easy Config Switcher client initialization starting");

        ModConfig.load();

        EasyConfigSwitcher.LOGGER.info("Easy Config Switcher client initialization complete");
    }

    public static class ModMenuConfigScreen implements ModMenuApi {
        @Override
        public ConfigScreenFactory<?> getModConfigScreenFactory() {
            return ConfigScreen.EasyConfigSwitcherConfigScreen::create;
        }
    }
}
