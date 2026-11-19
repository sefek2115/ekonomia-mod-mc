package pl.xmasbank.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import pl.xmasbank.HistoryEntry;
import pl.xmasbank.Net;

/** Stan po stronie klienta (saldo, historia, animacje). */
public final class BankClient {
	private BankClient() {}

	public record Popup(long delta, long start) {}
	public record Center(long delta, String note, long start) {}

	public static final long POPUP_MS = 2600;
	public static final long CENTER_MS = 4200;
	public static final long CHIP_MS = 3400;

	public static long balance = 0;
	public static List<HistoryEntry> history = new ArrayList<>();
	public static final List<Popup> popups = new ArrayList<>();
	public static Center center = null;
	public static long lastEvent = -100000;

	public static String statusText = "";
	public static boolean statusOk = true;
	public static int statusSeq = 0;

	public static void reset() {
		balance = 0;
		history = new ArrayList<>();
		popups.clear();
		center = null;
		lastEvent = -100000;
		statusText = "";
	}

	public static void onBalance(Net.BalancePayload p) {
		balance = p.balance();
		long now = Util.getMillis();
		if (p.delta() != 0) {
			popups.add(new Popup(p.delta(), now));
			while (popups.size() > 4) {
				popups.remove(0);
			}
			lastEvent = now;
			if (!p.note().isEmpty()) {
				center = new Center(p.delta(), p.note(), now);
			}
			if (Minecraft.getInstance().screen instanceof BankScreen) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new Net.RequestSyncPayload());
			}
		}
	}

	public static void onHistory(Net.HistoryPayload p) {
		history = new ArrayList<>(p.entries());
	}

	public static void onResult(Net.ResultPayload p) {
		statusText = p.message();
		statusOk = p.ok();
		statusSeq++;
	}

	public static void prune(long now) {
		popups.removeIf(p -> now - p.start() > POPUP_MS);
		if (center != null && now - center.start() > CENTER_MS) {
			center = null;
		}
	}
}
