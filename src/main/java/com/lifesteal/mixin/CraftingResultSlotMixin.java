package com.lifesteal.mixin;

import com.lifesteal.item.LifestealItems;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Converts any crafted Totem of Undying that is NOT already tagged as a
 * Lifesteal item into a Revive Totem.
 *
 * Vanilla has no recipe for totems, so any totem obtained via crafting must
 * have come from our revive_totem.json recipe.
 */
@Mixin(CraftingResultSlot.class)
public class CraftingResultSlotMixin {

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void lifesteal$convertToReviveTotem(PlayerEntity player,
                                                ItemStack stack,
                                                CallbackInfo ci) {
        if (stack.isOf(Items.TOTEM_OF_UNDYING) && !LifestealItems.isReviveTotem(stack)) {
            LifestealItems.applyReviveTotemData(stack);
        }
    }
}
