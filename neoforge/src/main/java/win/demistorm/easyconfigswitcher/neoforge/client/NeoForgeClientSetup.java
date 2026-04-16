package win.demistorm.easyconfigswitcher.neoforge.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.fml.ModLoadingContext;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;
import win.demistorm.easyconfigswitcher.client.ConfigScreen;
import win.demistorm.easyconfigswitcher.config.ModConfig;

public class NeoForgeClientSetup {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NeoForgeClientSetup::onClientSetup);

        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
            () -> (minecraft, screen) -> ConfigScreen.EasyConfigSwitcherConfigScreen.create(screen));
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        EasyConfigSwitcher.LOGGER.info("Easy Config Switcher client initialization starting");

        ModConfig.load();

        EasyConfigSwitcher.LOGGER.info("Easy Config Switcher client initialization complete");
    }
}
