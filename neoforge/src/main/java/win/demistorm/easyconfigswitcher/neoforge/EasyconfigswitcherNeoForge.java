package win.demistorm.easyconfigswitcher.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;
import win.demistorm.easyconfigswitcher.neoforge.client.NeoForgeClientSetup;

@Mod(EasyConfigSwitcher.MOD_ID)
public final class EasyConfigSwitcherNeoForge {
    public EasyConfigSwitcherNeoForge(IEventBus modEventBus) {
        EasyConfigSwitcher.initialize();

        if (FMLEnvironment.getDist().isClient()) {
            NeoForgeClientSetup.register(modEventBus);
        }
    }
}
