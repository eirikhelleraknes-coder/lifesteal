package com.lifesteal.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Factories and tag-detection helpers for Lifesteal custom items.
 *
 * Heart Item    – Nether Star with {lifesteal:{type:"heart"}}
 * Revive Totem  – Totem of Undying with {lifesteal:{type:"revive_totem"}}
 */
public final class LifestealItems {

    private LifestealItems() {}

    // -------------------------------------------------------------------------
    // Tag helpers
    // -------------------------------------------------------------------------

    private static final String LIFESTEAL_KEY  = "lifesteal";
    private static final String TYPE_KEY       = "type";
    private static final String TYPE_HEART     = "heart";
    private static final String TYPE_REVIVE    = "revive_totem";

    private static String getType(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) return "";
        NbtCompound nbt = customData.copyNbt();
        if (!nbt.contains(LIFESTEAL_KEY)) return "";
        return nbt.getCompound(LIFESTEAL_KEY)
                .flatMap(comp -> comp.getString(TYPE_KEY))
                .orElse("");
    }

    public static boolean isHeartItem(ItemStack stack) {
        return stack.isOf(Items.NETHER_STAR) && TYPE_HEART.equals(getType(stack));
    }

    public static boolean isReviveTotem(ItemStack stack) {
        return stack.isOf(Items.TOTEM_OF_UNDYING) && TYPE_REVIVE.equals(getType(stack));
    }

    // -------------------------------------------------------------------------
    // Item factories
    // -------------------------------------------------------------------------

    /** Creates a Heart Item (Nether Star with custom data, name, lore). */
    public static ItemStack createHeartItem() {
        ItemStack stack = new ItemStack(Items.NETHER_STAR);

        // Custom data tag
        NbtCompound lifestealNbt = new NbtCompound();
        lifestealNbt.putString(TYPE_KEY, TYPE_HEART);
        NbtCompound rootNbt = new NbtCompound();
        rootNbt.put(LIFESTEAL_KEY, lifestealNbt);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(rootNbt));

        // Name
        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("\u2764 Heart").formatted(Formatting.RED));

        // Lore
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Right-click to gain +1 max heart").formatted(Formatting.GRAY)
        )));

        return stack;
    }

    /** Creates a Revive Totem (Totem of Undying with custom data, name, glint). */
    public static ItemStack createReviveTotem() {
        ItemStack stack = new ItemStack(Items.TOTEM_OF_UNDYING);
        applyReviveTotemData(stack);
        return stack;
    }

    /**
     * Applies revive totem data in-place (used by the crafting mixin to
     * convert a plain crafted totem into a tagged revive totem).
     */
    public static void applyReviveTotemData(ItemStack stack) {
        NbtCompound lifestealNbt = new NbtCompound();
        lifestealNbt.putString(TYPE_KEY, TYPE_REVIVE);
        NbtCompound rootNbt = new NbtCompound();
        rootNbt.put(LIFESTEAL_KEY, lifestealNbt);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(rootNbt));

        stack.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal("Revive Totem").formatted(Formatting.GOLD));

        stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.literal("Right-click to open the revive menu").formatted(Formatting.GRAY)
        )));
    }
}
