package com.lifesteal;

import com.lifesteal.command.LifestealCommands;
import com.lifesteal.gui.ReviveGui;
import com.lifesteal.item.LifestealItems;
import com.lifesteal.manager.HeartManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Lifesteal implements ModInitializer {

	public static final String MOD_ID = "lifesteal";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Lifesteal SMP mod initializing...");

		// ----------------------------------------------------------------
		// Player join: assign / restore hearts and unlock recipe
		// ----------------------------------------------------------------
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			HeartManager.onPlayerJoin(player);
		});

		// ----------------------------------------------------------------
		// PvP kill: adjust hearts
		// ----------------------------------------------------------------
		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, killer, killed, damageSource) -> {
			if (killer instanceof ServerPlayerEntity killerPlayer && killed instanceof ServerPlayerEntity killedPlayer) {
				HeartManager.onPvpKill(killerPlayer, killedPlayer);
			}
		});

		// ----------------------------------------------------------------
		// Right-click items: Heart Item & Revive Totem
		// ----------------------------------------------------------------
		UseItemCallback.EVENT.register((player, world, hand) -> {
			if (world.isClient()) {
				return ActionResult.PASS;
			}

			var stack = player.getStackInHand(hand);

			if (LifestealItems.isHeartItem(stack) && player instanceof ServerPlayerEntity sp) {
				HeartManager.addHeart(sp);
				stack.decrement(1);
				world.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
				return ActionResult.SUCCESS;
			}

			if (LifestealItems.isReviveTotem(stack) && player instanceof ServerPlayerEntity sp) {
				ReviveGui.openStage1(sp, stack);
				return ActionResult.SUCCESS;
			}

			return ActionResult.PASS;
		});

		// ----------------------------------------------------------------
		// Commands: /ls withdraw|gift|revive
		// ----------------------------------------------------------------
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				LifestealCommands.register(dispatcher, registryAccess));

		LOGGER.info("Lifesteal SMP mod ready.");
	}
}