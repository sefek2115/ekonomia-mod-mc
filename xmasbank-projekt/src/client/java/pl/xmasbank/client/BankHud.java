package pl.xmasbank.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import pl.xmasbank.XmasBank;

/** Małe saldo w prawym górnym rogu (pod TAB lub chwilowo po zmianie) + animacje + kary na środku. */
public final class BankHud {
	private BankHud() {}

	static final Identifier COIN = Identifier.fromNamespaceAndPath(XmasBank.MOD_ID, "textures/gui/coin.png");
	private static final int GREEN = 0x55FF55;
	private static final int RED = 0xFF5555;

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.PLAYER_LIST,
				Identifier.fromNamespaceAndPath(XmasBank.MOD_ID, "balance"), BankHud::render);
	}

	static int argb(int alpha, int rgb) {
		return (Math.max(6, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}
		long now = Util.getMillis();
		BankClient.prune(now);
		Font font = mc.font;

		boolean tab = mc.options.keyPlayerList.isDown();
		boolean recent = now - BankClient.lastEvent < BankClient.CHIP_MS;
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();

		if (tab || recent) {
			String text = Long.toString(BankClient.balance);
			int tw = font.width(text);
			int w = 2 + 9 + 3 + tw + 3;
			int h = 11;
			int x = sw - w - 3;
			int y = 3;
			g.fill(x, y, x + w, y + h, 0x78000000);
			g.blit(RenderPipelines.GUI_TEXTURED, BankHud.COIN, x + 2, y + 1, 0, 0, 9, 9, 32, 32, 32, 32);
			g.text(font, text, x + 2 + 9 + 3, y + 2, 0xFFFFFFFF, true);

			int i = 0;
			for (int k = BankClient.popups.size() - 1; k >= 0; k--, i++) {
				BankClient.Popup p = BankClient.popups.get(k);
				float t = (now - p.start()) / (float) BankClient.POPUP_MS;
				int alpha = t < 0.7f ? 255 : (int) (255 * (1f - (t - 0.7f) / 0.3f));
				String s = (p.delta() > 0 ? "+" : "-") + Math.abs(p.delta());
				int py = y + h + 2 + i * 10 + (int) (t * 5);
				g.text(font, s, x + w - font.width(s) - 2, py, argb(alpha, p.delta() > 0 ? GREEN : RED), true);
			}
		}

		BankClient.Center c = BankClient.center;
		if (c != null) {
			float t = (now - c.start()) / (float) BankClient.CENTER_MS;
			int alpha = t < 0.75f ? 255 : (int) (255 * (1f - (t - 0.75f) / 0.25f));
			String big = (c.delta() > 0 ? "+" : "-") + Math.abs(c.delta());
			int cx = sw / 2;
			int cy = sh / 2 - 36;
			g.pose().pushMatrix();
			g.pose().translate((float) cx, (float) cy);
			g.pose().scale(2f, 2f);
			g.text(font, big, -font.width(big) / 2, 0, argb(alpha, c.delta() > 0 ? GREEN : RED), true);
			g.pose().popMatrix();
			String note = c.note();
			g.text(font, note, cx - font.width(note) / 2, cy + 22, argb(alpha, 0xFFFFFF), true);
		}
	}
}
