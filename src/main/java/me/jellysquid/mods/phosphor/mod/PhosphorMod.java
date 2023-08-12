package me.jellysquid.mods.phosphor.mod;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PhosphorMod implements ModInitializer {
    public static Logger LOGGER;
    public static PhosphorConfig CONFIG;

    @Override
    public void onInitialize() {
        LOGGER = LogManager.getLogger("phosphor-legacy");
        CONFIG = PhosphorConfig.loadConfig();
    }
}
