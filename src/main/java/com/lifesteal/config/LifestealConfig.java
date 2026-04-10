package com.lifesteal.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lifesteal.Lifesteal;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class LifestealConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static LifestealConfig instance;

    /** Hearts each player starts with. Default 10. */
    public int startingHearts = 10;

    /** Maximum hearts a player can have. 0 = unlimited. Default 20. */
    public int maxHearts = 20;

    /** Hearts a revived player starts with. Default 4. */
    public int reviveHearts = 4;

    public static LifestealConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static LifestealConfig load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("lifesteal.json");
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                LifestealConfig cfg = GSON.fromJson(reader, LifestealConfig.class);
                if (cfg != null) {
                    return cfg;
                }
            } catch (IOException e) {
                Lifesteal.LOGGER.error("Failed to load lifesteal config, using defaults", e);
            }
        }
        LifestealConfig defaultCfg = new LifestealConfig();
        defaultCfg.save(configPath);
        return defaultCfg;
    }

    private void save(Path path) {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            Lifesteal.LOGGER.error("Failed to save lifesteal config", e);
        }
    }
}
