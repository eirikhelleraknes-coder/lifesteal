package com.lifesteal.mixin;

import com.lifesteal.gui.ReviveGui;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts slot-click events so that the Revive GUI can handle them
 * without items being moved in/out of the container.
 */
@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin {

    @Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
    private void lifesteal$interceptReviveGuiClick(int slotIndex,
                                                   int button,
                                                   SlotActionType actionType,
                                                   PlayerEntity player,
                                                   CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        ReviveGui.Session session = ReviveGui.getSession(serverPlayer.getUuid());
        if (session == null) return;

        // Only intercept clicks on the GUI inventory (slots 0-26 for 3×9)
        if (slotIndex < 0 || slotIndex >= 27) return;

        // Cancel the default slot-click behaviour so items are never moved
        ci.cancel();

        ReviveGui.handleClick(serverPlayer, slotIndex);
    }

    @Inject(method = "onClosed", at = @At("HEAD"))
    private void lifesteal$clearSession(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer) {
            ReviveGui.clearSession(serverPlayer.getUuid());
        }
    }
}
