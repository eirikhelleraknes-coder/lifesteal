package com.lifesteal.data;

import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a UUID -> heart count mapping for the current server runtime.
 */
public class LifestealData {

    private static final Map<MinecraftServer, LifestealData> INSTANCES = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> hearts = new HashMap<>();

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the stored heart count, or -1 if the player has never joined. */
    public int getHearts(UUID uuid) {
        return hearts.getOrDefault(uuid, -1);
    }

    public void setHearts(UUID uuid, int amount) {
        hearts.put(uuid, amount);
    }

    public boolean hasData(UUID uuid) {
        return hearts.containsKey(uuid);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static LifestealData getOrCreate(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, s -> new LifestealData());
    }
}
