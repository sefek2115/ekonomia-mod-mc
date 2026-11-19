package pl.xmasbank;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmasBank implements ModInitializer {
	public static final String MOD_ID = "xmasbank";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
	public static Item COIN;

	@Override
	public void onInitialize() {
		Identifier coinId = Identifier.fromNamespaceAndPath(MOD_ID, "coin");
		ResourceKey<Item> coinKey = ResourceKey.create(Registries.ITEM, coinId);
		COIN = Registry.register(BuiltInRegistries.ITEM, coinKey, new Item(new Item.Properties().setId(coinKey)));

		Net.register();

		ServerLifecycleEvents.SERVER_STARTING.register(Bank::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(Bank::saveNow);
		ServerTickEvents.END_SERVER_TICK.register(Bank::onTick);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Bank.onJoin(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> Bank.onDisconnect(handler.getPlayer()));

		ServerPlayNetworking.registerGlobalReceiver(Net.RequestSyncPayload.TYPE,
				(payload, context) -> Bank.sendFullSync(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(Net.TransferPayload.TYPE,
				(payload, context) -> Bank.handleTransfer(context.player(), payload));

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer sp && !sp.isCreative()) {
				Bank.onBlockBroken(sp);
			}
		});

		ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((level, entity, killed, source) -> {
			if (entity instanceof ServerPlayer sp && killed instanceof Enemy) {
				Bank.onMobKilled(sp);
			}
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> BankCommands.register(dispatcher));
	}
}
