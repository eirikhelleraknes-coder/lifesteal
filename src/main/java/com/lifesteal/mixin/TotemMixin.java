package com.lifesteal.mixin;

import com.lifesteal.item.LifestealItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the vanilla totem-of-undying death-prevention logic from firing
 * when the player is holding a Lifesteal Revive Totem.
 */
@Mixin(LivingEntity.class)
public class TotemMixin {

    @Inject(method = "tryUseTotem", at = @At("HEAD"), cancellable = true)
    private void lifesteal$preventReviveTotemDeath(DamageSource source,
                                                   CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        for (Hand hand : Hand.values()) {
            ItemStack held = self.getStackInHand(hand);
            if (LifestealItems.isReviveTotem(held)) {
                // Block vanilla totem activation for our custom totem.
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
