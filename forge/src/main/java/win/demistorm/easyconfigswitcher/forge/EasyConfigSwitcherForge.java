package win.demistorm.easyconfigswitcher.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;
import win.demistorm.easyconfigswitcher.forge.client.ForgeClientSetup;

@Mod(EasyConfigSwitcher.MOD_ID)
public final class EasyConfigSwitcherForge {
    public EasyConfigSwitcherForge(FMLJavaModLoadingContext context) {
        EasyConfigSwitcher.initialize();

        if (FMLEnvironment.dist.isClient()) {
            ForgeClientSetup.register(context);
        }
    }
}
