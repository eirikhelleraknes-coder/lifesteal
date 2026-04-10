package com.lifesteal.gui;

import com.lifesteal.manager.HeartManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the two-stage Revive GUI.
 *
 * Stage 1 – 3×9 chest listing banned players as skulls; empty slots = gray panes.
 * Stage 2 – confirm/cancel for a single target.
 *
 * Click interception is performed by {@link com.lifesteal.mixin.ScreenHandlerMixin}.
 */
public final class ReviveGui {

    private ReviveGui() {}

    // -------------------------------------------------------------------------
    // Active session tracking
    // -------------------------------------------------------------------------

    public enum Stage { ONE, TWO }

    public record Session(
            Stage stage,
            SimpleInventory inventory,
            /** Populated in stage 2: name of the player being revived. */
            String targetName,
            /** The revive totem stack consumed when reviving (stage 2). */
            ItemStack totemStack
    ) {}

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    /**
     * Set of players currently in a stage-to-stage transition.
     * While transitioning, {@link #clearSession} is a no-op so that the
     * new session set before {@code openHandledScreen} isn't wiped by the
     * {@code onClosed} callback for the old screen.
     */
    private static final Set<UUID> TRANSITIONING = ConcurrentHashMap.newKeySet();

    public static Session getSession(UUID playerUuid) {
        return SESSIONS.get(playerUuid);
    }

    public static void clearSession(UUID playerUuid) {
        if (!TRANSITIONING.contains(playerUuid)) {
            SESSIONS.remove(playerUuid);
        }
    }

    // -------------------------------------------------------------------------
    // Stage 1 – list banned players
    // -------------------------------------------------------------------------

    public static void openStage1(ServerPlayerEntity player, ItemStack totemInHand) {
        MinecraftServer server = player.getCommandSource().getServer();
        if (server == null) return;

        SimpleInventory inv = new SimpleInventory(27);

        // Fill with gray panes first
        ItemStack pane = grayPane();
        for (int i = 0; i < 27; i++) inv.setStack(i, pane.copy());

        // Populate skulls for banned players
        List<String> candidates = getReviveCandidates(server, player.getUuid());
        for (int i = 0; i < Math.min(candidates.size(), 27); i++) {
            inv.setStack(i, skullFor(candidates.get(i)));
        }

        Session session = new Session(Stage.ONE, inv, null, totemInHand.copy());
        TRANSITIONING.add(player.getUuid());
        try {
            SESSIONS.put(player.getUuid(), session);
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, p) -> new GenericContainerScreenHandler(
                            ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, 3),
                    Text.literal("Revive Menu").formatted(Formatting.GOLD)
            ));
        } finally {
            TRANSITIONING.remove(player.getUuid());
        }
    }

    // -------------------------------------------------------------------------
    // Stage 2 – confirm / cancel
    // -------------------------------------------------------------------------

    public static void openStage2(ServerPlayerEntity player, String targetName) {
        Session current = SESSIONS.get(player.getUuid());
        if (current == null) return;

        SimpleInventory inv = new SimpleInventory(27);

        // Fill with gray panes
        ItemStack pane = grayPane();
        for (int i = 0; i < 27; i++) inv.setStack(i, pane.copy());

        // Target skull at slot 13 (center of 3×9 grid)
        inv.setStack(13, skullFor(targetName));

        // Green wool confirm at slot 11
        ItemStack confirm = new ItemStack(Items.LIME_WOOL);
        confirm.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Confirm – revive " + targetName).formatted(Formatting.GREEN));
        inv.setStack(11, confirm);

        // Red wool cancel at slot 15
        ItemStack cancel = new ItemStack(Items.RED_WOOL);
        cancel.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Cancel").formatted(Formatting.RED));
        inv.setStack(15, cancel);

        Session session = new Session(Stage.TWO, inv, targetName, current.totemStack());
        TRANSITIONING.add(player.getUuid());
        try {
            SESSIONS.put(player.getUuid(), session);
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, p) -> new GenericContainerScreenHandler(
                            ScreenHandlerType.GENERIC_9X3, syncId, playerInv, inv, 3),
                    Text.literal("Confirm Revive").formatted(Formatting.GOLD)
            ));
        } finally {
            TRANSITIONING.remove(player.getUuid());
        }
    }

    // -------------------------------------------------------------------------
    // Slot-click handling (called from ScreenHandlerMixin)
    // -------------------------------------------------------------------------

    /**
     * Handles a slot click in a revive GUI.
     *
     * @return true if the click was consumed (caller should cancel the event)
     */
    public static boolean handleClick(ServerPlayerEntity player, int slotIndex) {
        Session session = SESSIONS.get(player.getUuid());
        if (session == null) return false;

        if (session.stage() == Stage.ONE) {
            ItemStack clicked = session.inventory().getStack(slotIndex);
            if (clicked.isOf(Items.PLAYER_HEAD)) {
                String targetName = getSkullName(clicked);
                if (targetName != null && !targetName.isEmpty()) {
                    openStage2(player, targetName);
                }
            }
            return true;
        }

        if (session.stage() == Stage.TWO) {
            if (slotIndex == 11) {
                // Confirm – revive
                String target = session.targetName();
                MinecraftServer server = player.getCommandSource().getServer();
                if (target != null && server != null) {
                    boolean revived = HeartManager.revivePlayer(server, target);
                    if (revived) {
                        consumeTotem(player, session.totemStack());
                        player.sendMessage(
                                Text.literal("Revived " + target + "!").formatted(Formatting.GREEN),
                                false);
                    } else {
                        player.sendMessage(
                                Text.literal("Could not revive " + target + ".").formatted(Formatting.RED),
                                false);
                    }
                }
                SESSIONS.remove(player.getUuid());
                player.closeHandledScreen();
            } else if (slotIndex == 15) {
                // Cancel – back to stage 1
                openStage1(player, session.totemStack());
            }
            return true;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ItemStack grayPane() {
        ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return stack;
    }

    private static ItemStack skullFor(String profileName) {
        ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
        skull.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(profileName).formatted(Formatting.YELLOW));

        // Embed the name in custom data so we can read it back on click
        NbtCompound root = new NbtCompound();
        root.putString("reviveTarget", profileName);
        skull.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));

        return skull;
    }

    private static String getSkullName(ItemStack skull) {
        NbtComponent customData = skull.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return null;
        Optional<NbtCompound> nbt = customData.copyNbt();
        if (nbt.isEmpty()) return null;
        return nbt.get().getString("reviveTarget").orElse(null);
    }

    private static List<String> getReviveCandidates(MinecraftServer server, UUID excludeUuid) {
        List<String> names = new ArrayList<>();
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            if (!online.getUuid().equals(excludeUuid)) {
                names.add(online.getName().getString());
            }
        }
        return names;
    }

    /**
     * Removes one Revive Totem from the player's inventory.
     * Checks hands first, then the full inventory.
     */
    private static void consumeTotem(ServerPlayerEntity player, ItemStack totemStack) {
        for (net.minecraft.util.Hand hand : net.minecraft.util.Hand.values()) {
            ItemStack held = player.getStackInHand(hand);
            if (held.getItem() == totemStack.getItem()
                    && com.lifesteal.item.LifestealItems.isReviveTotem(held)) {
                held.decrement(1);
                return;
            }
        }
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == totemStack.getItem()
                    && com.lifesteal.item.LifestealItems.isReviveTotem(s)) {
                s.decrement(1);
                return;
            }
        }
    }
}
