package win.demistorm.easyconfigswitcher.fabric;

import net.fabricmc.api.ModInitializer;
import win.demistorm.easyconfigswitcher.EasyConfigSwitcher;

public final class EasyConfigSwitcherFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        EasyConfigSwitcher.initialize();
    }
}
