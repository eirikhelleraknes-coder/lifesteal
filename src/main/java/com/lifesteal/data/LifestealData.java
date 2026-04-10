package com.lifesteal.data;

import com.lifesteal.Lifesteal;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists a UUID -> heart count mapping in the world's PersistentState.
 */
public class LifestealData extends PersistentState {

    private static final String KEY = "lifesteal_hearts";
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
        markDirty();
    }

    public boolean hasData(UUID uuid) {
        return hearts.containsKey(uuid);
    }

    // -------------------------------------------------------------------------
    // Serialisation
    // -------------------------------------------------------------------------

    private static LifestealData fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        LifestealData data = new LifestealData();
        NbtCompound heartsNbt = nbt.getCompound("hearts");
        for (String key : heartsNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                int amount = heartsNbt.getInt(key);
                data.hearts.put(uuid, amount);
            } catch (IllegalArgumentException e) {
                Lifesteal.LOGGER.warn("Invalid UUID in lifesteal data: {}", key);
            }
        }
        return data;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtCompound heartsNbt = new NbtCompound();
        hearts.forEach((uuid, amount) -> heartsNbt.putInt(uuid.toString(), amount));
        nbt.put("hearts", heartsNbt);
        return nbt;
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static LifestealData getOrCreate(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(
                new PersistentState.Type<>(
                        LifestealData::new,
                        LifestealData::fromNbt,
                        null
                ),
                KEY
        );
    }
}
