package pl.xmasbank.client;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import pl.xmasbank.HistoryEntry;
import pl.xmasbank.Net;

/** Okno banku: przelew + historia. Rysowane w całości ręcznie, skaluje się do małych okien. */
public class BankScreen extends Screen {
	private static final int W = 256;
	private static final int H = 200;

	private static final int RED = 0xFFB3262E;
	private static final int GREEN = 0xFF2E7D32;
	private static final int[] BULBS = {0xFFE53935, 0xFF43A047, 0xFFFFC107, 0xFF1E88E5};
	private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM HH:mm");

	private static final class Field {
		final int x, y, w, h, max;
		final boolean numeric;
		String text = "";

		Field(int x, int y, int w, int h, int max, boolean numeric) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.max = max;
			this.numeric = numeric;
		}
	}

	private int tab = 0;
	private final Field fTarget = new Field(10, 64, 150, 14, 16, false);
	private final Field fAmount = new Field(10, 94, 70, 14, 9, true);
	private final Field fTitle = new Field(10, 124, 150, 14, 40, false);
	private Field focus = fTarget;
	private int scroll = 0;
	private int seenSeq = BankClient.statusSeq;

	private float s = 1f;
	private int ox, oy;
	private List<String> online = new ArrayList<>();
	private long nextOnlineRefresh = 0;

	public BankScreen() {
		super(Component.literal("Bank"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------- rysowanie ----------

	private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
		g.fill(x, y, x + w, y + h, c);
	}

	private static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		rect(g, x + 1, y, w - 2, h, 0xFF000000);
		rect(g, x, y + 1, w, h - 2, 0xFF000000);
		rect(g, x + 1, y + 1, w - 2, h - 2, 0xFF8B8B8B);
		rect(g, x + 2, y + 1, w - 4, 2, 0xFFC6C6C6);
		rect(g, x + 1, y + 2, 2, h - 4, 0xFFC6C6C6);
		rect(g, x + 2, y + h - 3, w - 3, 2, 0xFF555555);
		rect(g, x + w - 3, y + 2, 2, h - 4, 0xFF555555);
	}

	private static void slot(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		rect(g, x, y, w, h, 0xFF2B2B2B);
		rect(g, x + 1, y + 1, w - 1, h - 1, 0xFF9A9A9A);
		rect(g, x + 1, y + 1, w - 2, h - 2, 0xFF555555);
	}

	private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int color, boolean hover) {
		int c = hover ? lighten(color) : color;
		rect(g, x, y, w, h, 0xFF000000);
		rect(g, x + 1, y + 1, w - 2, h - 2, c);
		rect(g, x + 1, y + 1, w - 2, 1, lighten(c));
		rect(g, x + 1, y + h - 2, w - 2, 1, darken(c));
		g.text(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF, true);
	}

	private static int lighten(int c) {
		int r = Math.min(255, ((c >> 16) & 0xFF) + 40), gr = Math.min(255, ((c >> 8) & 0xFF) + 40), b = Math.min(255, (c & 0xFF) + 40);
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}

	private static int darken(int c) {
		return 0xFF000000 | (((c >> 17) & 0x7F) << 16) | (((c >> 9) & 0x7F) << 8) | ((c >> 1) & 0x7F);
	}

	private void field(GuiGraphicsExtractor g, Field f, long now) {
		slot(g, f.x, f.y, f.w, f.h);
		String shown = this.font.plainSubstrByWidth(f.text, f.w - 8, true);
		g.text(this.font, shown, f.x + 3, f.y + 3, 0xFFFFFFFF, false);
		if (focus == f && (now / 500) % 2 == 0) {
			int cx = f.x + 3 + this.font.width(shown);
			rect(g, cx, f.y + 2, 1, 10, 0xFFFFFFFF);
		}
	}

	private void candy(GuiGraphicsExtractor g, int x, int y, int h, int dir) {
		for (int i = 0; i < h; i++) {
			rect(g, x, y + 3 + i, 3, 1, ((i / 3) & 1) == 0 ? 0xFFE53935 : 0xFFFFFFFF);
		}
		for (int i = 0; i < 7; i++) {
			int px = dir > 0 ? x + i : x + 2 - i;
			rect(g, px, y, 1, 3, ((i / 3) & 1) == 0 ? 0xFFE53935 : 0xFFFFFFFF);
		}
	}

	private void garland(GuiGraphicsExtractor g, long now) {
		for (int x = 3; x < W - 3; x++) {
			int y = 4 + (int) (3 * Math.abs(Math.sin((x - 3) * Math.PI / 32.0)));
			rect(g, x, y, 1, 1, 0xFF1E5B2C);
		}
		for (int k = 0; k < 16; k++) {
			int bx = 3 + 16 * k;
			int y = 4 + (int) (3 * Math.abs(Math.sin((bx - 3) * Math.PI / 32.0)));
			int c = BULBS[k % 4];
			boolean on = ((now / 450 + k) % 3) != 0;
			if (!on) {
				c = darken(c);
			}
			rect(g, bx - 1, y + 1, 3, 4, c);
			if (on) {
				rect(g, bx, y + 1, 1, 1, 0xFFFFFFFF);
			}
		}
	}

	private void snowCap(GuiGraphicsExtractor g) {
		for (int x = 2; x < W - 8; x += 7) {
			int h = 2 + (x * 7 % 3);
			rect(g, x, 1 - h, 7, h + 1, 0xFFFFFFFF);
		}
	}

	private void falling(GuiGraphicsExtractor g, long now) {
		for (int i = 0; i < 28; i++) {
			int seed = i * 7919 + 13;
			float speed = 12f + (seed % 17);
			int px = (int) (((seed * 37L) % Math.max(1, this.width)) + 4 * Math.sin(now / 900.0 + i));
			int py = (int) (((now / 1000.0) * speed + (seed % 997)) % Math.max(1, this.height));
			int size = (i % 3 == 0) ? 2 : 1;
			rect(g, px, py, size, size, 0xB0FFFFFF);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		long now = Util.getMillis();
		BankClient.prune(now);

		// przyciemnione tło + padający śnieg
		g.fill(0, 0, this.width, this.height, 0xB0000000);
		falling(g, now);

		s = Math.min(1f, Math.min((this.width - 30f) / W, (this.height - 10f) / H));
		if (s < 0.3f) {
			s = 0.3f;
		}
		ox = (int) ((this.width - W * s) / 2f);
		oy = (int) ((this.height - H * s) / 2f);
		int lx = (int) ((mouseX - ox) / s);
		int ly = (int) ((mouseY - oy) / s);

		if (BankClient.statusSeq != seenSeq) {
			seenSeq = BankClient.statusSeq;
			if (BankClient.statusOk) {
				fAmount.text = "";
				fTitle.text = "";
			}
		}
		if (now > nextOnlineRefresh) {
			refreshOnline();
			nextOnlineRefresh = now + 1000;
		}

		g.pose().pushMatrix();
		g.pose().translate((float) ox, (float) oy);
		g.pose().scale(s, s);

		panel(g, 0, 0, W, H);
		candy(g, -9, 40, 90, 1);
		candy(g, W + 6, 40, 90, -1);
		snowCap(g);
		garland(g, now);

		g.text(this.font, "Świąteczny Bank", 10, 18, 0xFFFFFFFF, true);
		String bal = Long.toString(BankClient.balance);
		int bw = this.font.width(bal);
		g.blit(RenderPipelines.GUI_TEXTURED, BankHud.COIN, W - 14 - bw - 12, 17, 0, 0, 10, 10, 32, 32, 32, 32);
		g.text(this.font, bal, W - 12 - bw, 18, 0xFFFFFFFF, true);

		button(g, 10, 32, 70, 16, "Przelew", tab == 0 ? RED : GREEN, tab == 0 || in(lx, ly, 10, 32, 70, 16));
		button(g, 84, 32, 70, 16, "Historia", tab == 1 ? RED : GREEN, tab == 1 || in(lx, ly, 84, 32, 70, 16));

		if (tab == 0) {
			drawTransfer(g, lx, ly, now);
		} else {
			drawHistory(g);
		}

		g.pose().popMatrix();
	}

	private void drawTransfer(GuiGraphicsExtractor g, int lx, int ly, long now) {
		g.text(this.font, "Odbiorca:", 10, 54, 0xFF404040, false);
		field(g, fTarget, now);
		g.text(this.font, "Kwota:", 10, 84, 0xFF404040, false);
		field(g, fAmount, now);
		String[] q = {"10", "50", "MAX"};
		for (int i = 0; i < 3; i++) {
			int bx = 84 + i * 26;
			button(g, bx, 94, 24, 14, q[i], 0xFF6B6B6B, in(lx, ly, bx, 94, 24, 14));
		}
		g.text(this.font, "Temat:", 10, 114, 0xFF404040, false);
		field(g, fTitle, now);

		g.text(this.font, "Online (kliknij):", 170, 54, 0xFF404040, false);
		for (int i = 0; i < 6; i++) {
			int y = 64 + i * 13;
			slot(g, 170, y, 76, 12);
			if (i < online.size()) {
				String n = this.font.plainSubstrByWidth(online.get(i), 70);
				boolean hov = in(lx, ly, 170, y, 76, 12);
				g.text(this.font, n, 174, y + 2, hov ? 0xFFFFFF55 : 0xFFFFFFFF, false);
			}
		}

		button(g, 10, 146, 80, 18, "Wyślij", GREEN, in(lx, ly, 10, 146, 80, 18));
		if (!BankClient.statusText.isEmpty()) {
			String st = this.font.plainSubstrByWidth(BankClient.statusText, W - 20);
			g.text(this.font, st, 10, 172, BankClient.statusOk ? 0xFF1B5E20 : 0xFF8B0000, false);
		}
	}

	private void drawHistory(GuiGraphicsExtractor g) {
		g.text(this.font, "Data", 10, 54, 0xFF404040, false);
		g.text(this.font, "Kwota", 66, 54, 0xFF404040, false);
		g.text(this.font, "Opis", 110, 54, 0xFF404040, false);
		List<HistoryEntry> list = BankClient.history;
		int rows = 10;
		int maxScroll = Math.max(0, list.size() - rows);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		slot(g, 8, 64, W - 16, rows * 12 + 4);
		if (list.isEmpty()) {
			g.text(this.font, "Brak transakcji.", 14, 70, 0xFFCCCCCC, false);
		}
		for (int i = 0; i < rows && i + scroll < list.size(); i++) {
			HistoryEntry e = list.get(i + scroll);
			int y = 67 + i * 12;
			g.text(this.font, DATE.format(new Date(e.time())), 12, y, 0xFFCCCCCC, false);
			String amt = (e.amount() > 0 ? "+" : "") + e.amount();
			g.text(this.font, amt, 66, y, e.amount() > 0 ? 0xFF55FF55 : 0xFFFF5555, false);
			g.text(this.font, this.font.plainSubstrByWidth(describe(e), W - 126), 110, y, 0xFFFFFFFF, false);
		}
		if (maxScroll > 0) {
			int trackH = rows * 12;
			int barH = Math.max(8, trackH * rows / list.size());
			int barY = 66 + (trackH - barH) * scroll / maxScroll;
			rect(g, W - 14, barY, 3, barH, 0xFFC6C6C6);
		}
	}

	private static String describe(HistoryEntry e) {
		return switch (e.kind()) {
			case "in" -> "Od " + e.other() + (e.title().isEmpty() ? "" : ": " + e.title());
			case "out" -> "Do " + e.other() + (e.title().isEmpty() ? "" : ": " + e.title());
			default -> e.title();
		};
	}

	private void refreshOnline() {
		List<String> names = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null && mc.player != null) {
			String me = mc.player.getName().getString();
			for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
				String n = info.getProfile().name();
				if (!n.equals(me)) {
					names.add(n);
				}
			}
		}
		names.sort(Comparator.comparing(String::toLowerCase));
		online = names;
	}

	// ---------- sterowanie ----------

	private static boolean in(int px, int py, int x, int y, int w, int h) {
		return px >= x && px < x + w && py >= y && py < y + h;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() != 0) {
			return super.mouseClicked(event, doubleClick);
		}
		int lx = (int) ((event.x() - ox) / s);
		int ly = (int) ((event.y() - oy) / s);

		if (in(lx, ly, 10, 32, 70, 16)) {
			tab = 0;
			return true;
		}
		if (in(lx, ly, 84, 32, 70, 16)) {
			tab = 1;
			return true;
		}
		if (tab == 0) {
			for (Field f : new Field[] {fTarget, fAmount, fTitle}) {
				if (in(lx, ly, f.x, f.y, f.w, f.h)) {
					focus = f;
					return true;
				}
			}
			String[] q = {"10", "50", "MAX"};
			for (int i = 0; i < 3; i++) {
				if (in(lx, ly, 84 + i * 26, 94, 24, 14)) {
					fAmount.text = q[i].equals("MAX") ? Long.toString(BankClient.balance) : q[i];
					return true;
				}
			}
			for (int i = 0; i < 6 && i < online.size(); i++) {
				if (in(lx, ly, 170, 64 + i * 13, 76, 12)) {
					fTarget.text = online.get(i);
					focus = fAmount;
					return true;
				}
			}
			if (in(lx, ly, 10, 146, 80, 18)) {
				send();
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == 1) {
			scroll -= (int) Math.signum(scrollY) * 2;
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (tab != 0 || focus == null) {
			return super.charTyped(event);
		}
		int cp = event.codepoint();
		if (cp < 32 || cp == 127) {
			return false;
		}
		if (focus.numeric && (cp < '0' || cp > '9')) {
			return true;
		}
		if (focus.text.length() < focus.max) {
			focus.text += new String(Character.toChars(cp));
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (tab == 0 && focus != null) {
			if (key == 259) { // Backspace
				if (!focus.text.isEmpty()) {
					focus.text = focus.text.substring(0, focus.text.length() - 1);
				}
				return true;
			}
			if (key == 258) { // Tab
				focus = focus == fTarget ? fAmount : focus == fAmount ? fTitle : fTarget;
				return true;
			}
			if (key == 257 || key == 335) { // Enter
				send();
				return true;
			}
		}
		return super.keyPressed(event);
	}

	private void send() {
		String target = fTarget.text.trim();
		long amount;
		try {
			amount = fAmount.text.isEmpty() ? 0 : Long.parseLong(fAmount.text);
		} catch (NumberFormatException e) {
			amount = 0;
		}
		if (target.isEmpty()) {
			setStatus("Wpisz nick odbiorcy.", false);
			return;
		}
		if (amount <= 0) {
			setStatus("Wpisz kwotę większą od 0.", false);
			return;
		}
		if (amount > BankClient.balance) {
			setStatus("Za mało środków na koncie.", false);
			return;
		}
		ClientPlayNetworking.send(new Net.TransferPayload(target, amount, fTitle.text.trim()));
		setStatus("Wysyłanie...", true);
	}

	private void setStatus(String text, boolean ok) {
		BankClient.statusText = text;
		BankClient.statusOk = ok;
	}
}
