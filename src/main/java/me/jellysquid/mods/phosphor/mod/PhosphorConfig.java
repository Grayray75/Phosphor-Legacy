package me.jellysquid.mods.phosphor.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;

// This class will be initialized very early and should never load any game/mod code.
public class PhosphorConfig {
    private static final Gson GSON = createGson();

    @SerializedName("enable_illegal_thread_access_warnings")
    public boolean enableIllegalThreadAccessWarnings = true;

    public static PhosphorConfig loadConfig() {
        File file = getConfigFile();
        PhosphorConfig config;

        if (!file.exists()) {
            config = new PhosphorConfig();
            config.saveConfig();
        }
        else {
            try (Reader reader = new FileReader(file)) {
                config = GSON.fromJson(reader, PhosphorConfig.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to deserialize config from disk", e);
            }
        }

        return config;
    }

    public void saveConfig() {
        try (Writer writer = new FileWriter(getConfigFile())) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize config to disk", e);
        }
    }

    private static File getConfigFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toString(), "phosphor-legacy.json");
    }

    private static Gson createGson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }
}
