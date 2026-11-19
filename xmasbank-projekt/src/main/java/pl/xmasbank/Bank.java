package pl.xmasbank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;

/** Cała logika serwerowa: konta, zarabianie, kary AFK, przelewy, zapis. */
public final class Bank {
	private Bank() {}

	public static final long START_BALANCE = 20;
	private static final int MAX_HISTORY = 60;
	private static final long MAX_AMOUNT = 1_000_000_000L;

	// zarobki
	private static final int HOUR_TICKS = 72000;
	private static final int REWARD_HOUR = 5;
	private static final int REWARD_ADV = 10;
	private static final int BLOCKS_NEEDED = 250;
	private static final int REWARD_BLOCKS = 15;
	private static final int KILLS_NEEDED = 50;
	private static final int REWARD_KILLS = 15;

	// kary AFK (ticki)
	private static final int MINE_AFK_TICKS = 20 * 60 * 20;   // 20 min
	private static final int MINE_GAP_TICKS = 20 * 120;       // przerwa >2 min resetuje licznik
	private static final int KILL_AFK_TICKS = 20 * 60 * 30;   // 30 min
	private static final int KILL_GAP_TICKS = 20 * 60 * 5;    // przerwa >5 min resetuje licznik
	private static final int PEN_MINE = 30;
	private static final int PEN_KILL = 50;
	private static final int PEN_IDLE = 5;
	private static final double SPOT_RADIUS_SQ = 9.0;         // "w miejscu" = w promieniu 3 bloków

	static final class Account {
		String name = "";
		long balance;
		long playTicks;
		int blocks;
		int kills;
		int unread;
		boolean advBase;
		Set<String> advDone = new HashSet<>();
		List<HistoryEntry> history = new ArrayList<>();
	}

	static final class Afk {
		double x, y, z;
		float yaw, pitch;
		int idleSince = -1;
		double mx, my, mz;
		int mineStart = -1, mineLast = -1;
		double kx, ky, kz;
		int killStart = -1, killLast = -1;
	}

	private static final Gson GSON = new GsonBuilder().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, Account>>() {}.getType();
	private static final Map<String, Account> ACCOUNTS = new HashMap<>();
	private static final Map<UUID, Afk> AFK = new HashMap<>();
	private static Path file;
	private static boolean dirty;
	private static MinecraftServer server;

	// ---------- zapis / odczyt ----------

	public static void load(MinecraftServer s) {
		server = s;
		ACCOUNTS.clear();
		AFK.clear();
		file = s.getWorldPath(LevelResource.ROOT).resolve("xmasbank.json");
		if (Files.exists(file)) {
			try {
				String json = Files.readString(file, StandardCharsets.UTF_8);
				Map<String, Account> loaded = GSON.fromJson(json, MAP_TYPE);
				if (loaded != null) {
					ACCOUNTS.putAll(loaded);
				}
			} catch (Exception e) {
				XmasBank.LOG.error("Nie udało się wczytać xmasbank.json", e);
			}
		}
	}

	public static void saveNow(MinecraftServer s) {
		if (file == null) {
			return;
		}
		try {
			Path tmp = file.resolveSibling("xmasbank.json.tmp");
			Files.writeString(tmp, GSON.toJson(ACCOUNTS), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException e) {
			XmasBank.LOG.error("Nie udało się zapisać xmasbank.json", e);
		}
	}

	// ---------- konta ----------

	private static String playerName(ServerPlayer p) {
		return p.getName().getString();
	}

	private static Account acc(ServerPlayer p) {
		String key = p.getUUID().toString();
		Account a = ACCOUNTS.get(key);
		if (a == null) {
			a = new Account();
			a.balance = START_BALANCE;
			addHistory(a, START_BALANCE, "sys", "", "Bonus startowy");
			ACCOUNTS.put(key, a);
			baselineAdvancements(p, a);
			dirty = true;
		}
		String n = playerName(p);
		if (!n.equals(a.name)) {
			a.name = n;
			dirty = true;
		}
		return a;
	}

	private static void addHistory(Account a, long amount, String kind, String other, String title) {
		a.history.add(new HistoryEntry(System.currentTimeMillis(), amount, kind, other == null ? "" : other, title == null ? "" : title));
		while (a.history.size() > MAX_HISTORY) {
			a.history.remove(0);
		}
	}

	public static long balance(ServerPlayer p) {
		return acc(p).balance;
	}

	/** Zmienia saldo (nie schodzi poniżej 0), zapisuje w historii i wysyła animację do gracza. Zwraca realnie zmienioną kwotę. */
	public static long apply(ServerPlayer p, long delta, String kind, String other, String title, String note) {
		Account a = acc(p);
		long applied = delta;
		if (delta < 0 && a.balance + delta < 0) {
			applied = -a.balance;
		}
		if (applied == 0) {
			return 0;
		}
		a.balance += applied;
		addHistory(a, applied, kind, other, title);
		dirty = true;
		ServerPlayNetworking.send(p, new Net.BalancePayload(a.balance, applied, note == null ? "" : note));
		return applied;
	}

	private static void penalty(ServerPlayer p, int amount, String reason) {
		apply(p, -amount, "sys", "", reason, reason);
	}

	// ---------- synchronizacja ----------

	public static void onJoin(ServerPlayer p) {
		Account a = acc(p);
		ServerPlayNetworking.send(p, new Net.BalancePayload(a.balance, 0, ""));
		if (a.unread > 0) {
			p.sendSystemMessage(Component.literal("§a[Bank] §fMasz nowe przelewy: §e" + a.unread + " §f(zakładka Historia)."));
			a.unread = 0;
			dirty = true;
		}
	}

	public static void onDisconnect(ServerPlayer p) {
		AFK.remove(p.getUUID());
	}

	public static void sendFullSync(ServerPlayer p) {
		Account a = acc(p);
		List<HistoryEntry> rev = new ArrayList<>(a.history);
		java.util.Collections.reverse(rev);
		ServerPlayNetworking.send(p, new Net.BalancePayload(a.balance, 0, ""));
		ServerPlayNetworking.send(p, new Net.HistoryPayload(rev));
	}

	// ---------- przelewy ----------

	public static void handleTransfer(ServerPlayer from, Net.TransferPayload pl) {
		String err = transfer(from, pl.target().trim(), pl.amount(), pl.title().trim());
		if (err != null) {
			ServerPlayNetworking.send(from, new Net.ResultPayload(false, err));
		} else {
			ServerPlayNetworking.send(from, new Net.ResultPayload(true, "Przelew wysłany!"));
			sendFullSync(from);
		}
	}

	private static String transfer(ServerPlayer from, String targetName, long amount, String title) {
		if (targetName.isEmpty()) {
			return "Wpisz nick odbiorcy.";
		}
		if (amount <= 0 || amount > MAX_AMOUNT) {
			return "Nieprawidłowa kwota.";
		}
		if (title.length() > 40) {
			title = title.substring(0, 40);
		}
		Account fromAcc = acc(from);
		if (fromAcc.balance < amount) {
			return "Za mało środków na koncie.";
		}

		ServerPlayer online = server.getPlayerList().getPlayerByName(targetName);
		UUID targetId = null;
		String targetResolved = targetName;
		if (online != null) {
			targetId = online.getUUID();
			targetResolved = playerName(online);
		} else {
			for (Map.Entry<String, Account> e : ACCOUNTS.entrySet()) {
				if (e.getValue().name.equalsIgnoreCase(targetName)) {
					targetId = UUID.fromString(e.getKey());
					targetResolved = e.getValue().name;
					break;
				}
			}
		}
		if (targetId == null) {
			return "Nie znaleziono gracza: " + targetName;
		}
		if (targetId.equals(from.getUUID())) {
			return "Nie możesz wysłać przelewu sam sobie.";
		}

		String senderName = playerName(from);
		apply(from, -amount, "out", targetResolved, title, "");
		if (online != null) {
			apply(online, amount, "in", senderName, title, "");
			String msg = "§a[Bank] §fPrzelew od §e" + senderName
					+ (title.isEmpty() ? "" : " §7| §fTemat: §b" + title)
					+ " §7| §a+" + amount;
			online.sendSystemMessage(Component.literal(msg));
		} else {
			Account ta = ACCOUNTS.get(targetId.toString());
			ta.balance += amount;
			addHistory(ta, amount, "in", senderName, title);
			ta.unread++;
			dirty = true;
		}
		return null;
	}

	// ---------- zarabianie i AFK ----------

	private static Afk afk(ServerPlayer p) {
		return AFK.computeIfAbsent(p.getUUID(), k -> new Afk());
	}

	public static void onTick(MinecraftServer s) {
		int now = s.getTickCount();
		if (now % 20 != 0) {
			return;
		}
		for (ServerPlayer p : s.getPlayerList().getPlayers()) {
			Account a = acc(p);

			// czas gry
			a.playTicks += 20;
			if (a.playTicks >= HOUR_TICKS) {
				a.playTicks -= HOUR_TICKS;
				apply(p, REWARD_HOUR, "sys", "", "Godzina gry", "");
			}

			// bezruch
			Afk f = afk(p);
			double dx = p.getX() - f.x, dy = p.getY() - f.y, dz = p.getZ() - f.z;
			boolean moved = dx * dx + dy * dy + dz * dz > 0.25
					|| Math.abs(Mth.wrapDegrees(p.getYRot() - f.yaw)) > 1.0f
					|| Math.abs(p.getXRot() - f.pitch) > 1.0f;
			if (moved || f.idleSince < 0) {
				f.x = p.getX();
				f.y = p.getY();
				f.z = p.getZ();
				f.yaw = p.getYRot();
				f.pitch = p.getXRot();
				f.idleSince = now;
			} else if (now - f.idleSince >= HOUR_TICKS) {
				f.idleSince = now;
				penalty(p, PEN_IDLE, "AFK: brak ruchu przez godzinę");
			}

			// osiągnięcia (co 10 s)
			if (now % 200 == 0) {
				pollAdvancements(p, a);
			}
		}
		if (dirty && now % 6000 == 0) {
			saveNow(s);
		}
	}

	public static void onBlockBroken(ServerPlayer p) {
		Account a = acc(p);
		a.blocks++;
		dirty = true;
		if (a.blocks >= BLOCKS_NEEDED) {
			a.blocks -= BLOCKS_NEEDED;
			apply(p, REWARD_BLOCKS, "sys", "", "Wykopano " + BLOCKS_NEEDED + " bloków", "");
		}
		int now = server.getTickCount();
		Afk f = afk(p);
		double dx = p.getX() - f.mx, dy = p.getY() - f.my, dz = p.getZ() - f.mz;
		if (f.mineStart < 0 || now - f.mineLast > MINE_GAP_TICKS || dx * dx + dy * dy + dz * dz > SPOT_RADIUS_SQ) {
			f.mineStart = now;
			f.mx = p.getX();
			f.my = p.getY();
			f.mz = p.getZ();
		}
		f.mineLast = now;
		if (now - f.mineStart >= MINE_AFK_TICKS) {
			f.mineStart = now;
			penalty(p, PEN_MINE, "AFK: kopanie w miejscu przez 20 min");
		}
	}

	public static void onMobKilled(ServerPlayer p) {
		Account a = acc(p);
		a.kills++;
		dirty = true;
		if (a.kills >= KILLS_NEEDED) {
			a.kills -= KILLS_NEEDED;
			apply(p, REWARD_KILLS, "sys", "", "Zabito " + KILLS_NEEDED + " mobów", "");
		}
		int now = server.getTickCount();
		Afk f = afk(p);
		double dx = p.getX() - f.kx, dy = p.getY() - f.ky, dz = p.getZ() - f.kz;
		if (f.killStart < 0 || now - f.killLast > KILL_GAP_TICKS || dx * dx + dy * dy + dz * dz > SPOT_RADIUS_SQ) {
			f.killStart = now;
			f.kx = p.getX();
			f.ky = p.getY();
			f.kz = p.getZ();
		}
		f.killLast = now;
		if (now - f.killStart >= KILL_AFK_TICKS) {
			f.killStart = now;
			penalty(p, PEN_KILL, "AFK: zabijanie mobów w miejscu przez 30 min");
		}
	}

	// ---------- osiągnięcia (bez mixinów: odpytywanie postępu) ----------

	private static void baselineAdvancements(ServerPlayer p, Account a) {
		if (server == null) {
			return;
		}
		for (AdvancementHolder h : server.getAdvancements().getAllAdvancements()) {
			if (isCountable(h) && p.getAdvancements().getOrStartProgress(h).isDone()) {
				a.advDone.add(h.id().toString());
			}
		}
		a.advBase = true;
	}

	private static boolean isCountable(AdvancementHolder h) {
		Advancement adv = h.value();
		return adv.display().isPresent() && adv.parent().isPresent();
	}

	private static void pollAdvancements(ServerPlayer p, Account a) {
		for (AdvancementHolder h : server.getAdvancements().getAllAdvancements()) {
			if (!isCountable(h)) {
				continue;
			}
			String id = h.id().toString();
			if (a.advDone.contains(id)) {
				continue;
			}
			if (p.getAdvancements().getOrStartProgress(h).isDone()) {
				a.advDone.add(id);
				dirty = true;
				String name = h.value().display().get().getTitle().getString();
				apply(p, REWARD_ADV, "sys", "", "Osiągnięcie: " + name, "");
			}
		}
	}
}
