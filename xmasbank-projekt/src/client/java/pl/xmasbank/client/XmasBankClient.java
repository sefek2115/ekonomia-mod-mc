package pl.xmasbank.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import pl.xmasbank.Net;
import pl.xmasbank.XmasBank;

public class XmasBankClient implements ClientModInitializer {
	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(XmasBank.MOD_ID, "main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.xmasbank.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, category));

		ClientPlayNetworking.registerGlobalReceiver(Net.BalancePayload.TYPE,
				(payload, context) -> BankClient.onBalance(payload));
		ClientPlayNetworking.registerGlobalReceiver(Net.HistoryPayload.TYPE,
				(payload, context) -> BankClient.onHistory(payload));
		ClientPlayNetworking.registerGlobalReceiver(Net.ResultPayload.TYPE,
				(payload, context) -> BankClient.onResult(payload));

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BankClient.reset());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.consumeClick()) {
				if (client.player != null && client.screen == null) {
					ClientPlayNetworking.send(new Net.RequestSyncPayload());
					client.setScreen(new BankScreen());
				}
			}
		});

		BankHud.init();
	}
}
