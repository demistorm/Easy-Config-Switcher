package win.demistorm.easyconfigswitcher.forge.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;
import win.demistorm.easyconfigswitcher.client.ConfigScreen;
import win.demistorm.easyconfigswitcher.config.ModConfig;

public class ForgeClientSetup {

    public static void register(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(ForgeClientSetup::onClientSetup);

        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) ->
                ConfigScreen.EasyConfigSwitcherConfigScreen.create(screen)));
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        EasyConfigSwitcher.LOGGER.info("Easy Config Switcher client initialization starting");

        ModConfig.load();

        EasyConfigSwitcher.LOGGER.info("Easy Config Switcher client initialization complete");
    }
}
