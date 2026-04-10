package com.lifesteal.manager;

import com.lifesteal.Lifesteal;
import com.lifesteal.config.LifestealConfig;
import com.lifesteal.data.LifestealData;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Core heart management: reading/writing heart counts, applying max-health,
 * banning players at 0 hearts, and reviving them.
 */
public final class HeartManager {

    private HeartManager() {}

    // -------------------------------------------------------------------------
    // HP conversion: 1 heart = 2 HP
    // -------------------------------------------------------------------------

    private static double heartsToHp(int hearts) {
        return hearts * 2.0;
    }

    // -------------------------------------------------------------------------
    // Applying stored hearts to online players
    // -------------------------------------------------------------------------

    /** Applies the stored heart count as max health to the given player. */
    public static void applyHearts(ServerPlayerEntity player, int hearts) {
        EntityAttributeInstance attr =
                player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (attr != null) {
            double hp = heartsToHp(hearts);
            attr.setBaseValue(hp);
            // Clamp current health so it never exceeds the new maximum
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
        }
    }

    // -------------------------------------------------------------------------
    // First-join & re-join logic
    // -------------------------------------------------------------------------

    /**
     * Called when a player joins. If they have no stored data, assigns the
     * configured starting hearts. Always restores max health from stored value.
     */
    public static void onPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getServerWorld().getServer();
        if (server == null) return;

        LifestealConfig cfg  = LifestealConfig.get();
        LifestealData   data = LifestealData.getOrCreate(server);
        UUID            uuid = player.getUuid();

        if (!data.hasData(uuid)) {
            data.setHearts(uuid, cfg.startingHearts);
        }

        int hearts = data.getHearts(uuid);
        applyHearts(player, hearts);
    }

    // -------------------------------------------------------------------------
    // PvP kill outcome
    // -------------------------------------------------------------------------

    /**
     * Called when {@code killer} kills {@code victim} in PvP.
     * Killer gains +1 heart; victim loses 1 heart (banning if it reaches 0).
     */
    public static void onPvpKill(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        addHeart(killer);
        removeHeart(victim);
    }

    // -------------------------------------------------------------------------
    // Mutation helpers
    // -------------------------------------------------------------------------

    /** Adds one heart to the player (clamped to maxHearts if > 0). */
    public static void addHeart(ServerPlayerEntity player) {
        MinecraftServer server = player.getServerWorld().getServer();
        if (server == null) return;

        LifestealConfig cfg  = LifestealConfig.get();
        LifestealData   data = LifestealData.getOrCreate(server);
        UUID            uuid = player.getUuid();

        int current = getStoredHearts(data, uuid, cfg);
        int next    = current + 1;
        if (cfg.maxHearts > 0 && next > cfg.maxHearts) {
            next = cfg.maxHearts;
        }
        data.setHearts(uuid, next);
        applyHearts(player, next);

        // Action-bar title: "+1 ❤"
        player.sendMessage(Text.literal("+1 ❤").formatted(Formatting.RED), true);
    }

    /**
     * Removes one heart from the player. Bans them if hearts reach 0 or below.
     */
    public static void removeHeart(ServerPlayerEntity victim) {
        MinecraftServer server = victim.getServerWorld().getServer();
        if (server == null) return;

        LifestealConfig cfg  = LifestealConfig.get();
        LifestealData   data = LifestealData.getOrCreate(server);
        UUID            uuid = victim.getUuid();

        int current = getStoredHearts(data, uuid, cfg);
        int next    = current - 1;

        if (next <= 0) {
            data.setHearts(uuid, 0);
            banPlayer(victim);
        } else {
            data.setHearts(uuid, next);
            applyHearts(victim, next);
        }
    }

    // -------------------------------------------------------------------------
    // Revive
    // -------------------------------------------------------------------------

    /**
     * Revives a banned player: unbans them and resets their heart count to the
     * configured reviveHearts value.
     *
     * @param server     the running server
     * @param targetName the (case-insensitive) name of the banned player
     * @return true if the player was found in the ban list and revived
     */
    public static boolean revivePlayer(MinecraftServer server, String targetName) {
        PlayerManager pm      = server.getPlayerManager();
        BannedPlayerList banList = pm.getUserBanList();

        for (BannedPlayerEntry entry : banList.values()) {
            PlayerConfigEntry playerEntry = entry.getKey();
            if (playerEntry.name().equalsIgnoreCase(targetName)) {
                UUID uuid = playerEntry.id();

                // Unban
                banList.remove(playerEntry);

                // Reset hearts
                LifestealConfig cfg  = LifestealConfig.get();
                LifestealData   data = LifestealData.getOrCreate(server);
                data.setHearts(uuid, cfg.reviveHearts);

                Lifesteal.LOGGER.info("Revived player {} ({})", playerEntry.name(), uuid);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Ban
    // -------------------------------------------------------------------------

    private static void banPlayer(ServerPlayerEntity player) {
        GameProfile profile = player.getGameProfile();
        BannedPlayerList banList = player.getServerWorld().getServer().getPlayerManager().getUserBanList();
        PlayerConfigEntry playerEntry = new PlayerConfigEntry(profile);

        if (!banList.contains(playerEntry)) {
            BannedPlayerEntry entry = new BannedPlayerEntry(
                    playerEntry, null, null, null,
                    "You ran out of hearts in Lifesteal!"
            );
            banList.add(entry);
        }

        player.networkHandler.disconnect(
                Text.literal("You have been banned – you ran out of hearts!")
                        .formatted(Formatting.RED)
        );
    }

    // -------------------------------------------------------------------------
    // Convenience
    // -------------------------------------------------------------------------

    private static int getStoredHearts(LifestealData data, UUID uuid, LifestealConfig cfg) {
        int v = data.getHearts(uuid);
        return (v < 0) ? cfg.startingHearts : v;
    }

    /** Returns the current stored heart count for an online player. */
    public static int getHearts(ServerPlayerEntity player) {
        MinecraftServer server = player.getServerWorld().getServer();
        if (server == null) return LifestealConfig.get().startingHearts;
        LifestealData data = LifestealData.getOrCreate(server);
        int v = data.getHearts(player.getUuid());
        return (v < 0) ? LifestealConfig.get().startingHearts : v;
    }
}