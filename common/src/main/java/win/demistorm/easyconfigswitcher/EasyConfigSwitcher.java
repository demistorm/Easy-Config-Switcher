package win.demistorm.easyconfigswitcher;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

public final class EasyConfigSwitcher {
    public static final String MOD_ID = "easyconfigswitcher";
    public static final Logger LOGGER = LogManager.getLogger(EasyConfigSwitcher.class);

    public static final boolean debugMode = false;

    static {
        Configurator.setLevel(EasyConfigSwitcher.class.getName(), debugMode ? Level.DEBUG : Level.INFO);
    }

    public static void initialize() {
        ShutdownHookManager.register();
    }
}
