package pl.xmasbank;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Wszystkie pakiety moda (serwer <-> klient). */
public final class Net {
	private Net() {}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(XmasBank.MOD_ID, path);
	}

	/** Serwer -> klient: aktualne saldo, delta (0 = tylko synchronizacja), opcjonalna notatka na środek ekranu (kary). */
	public record BalancePayload(long balance, long delta, String note) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<BalancePayload> TYPE = new CustomPacketPayload.Type<>(id("balance"));
		public static final StreamCodec<RegistryFriendlyByteBuf, BalancePayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, BalancePayload::balance,
				ByteBufCodecs.VAR_LONG, BalancePayload::delta,
				ByteBufCodecs.STRING_UTF8, BalancePayload::note,
				BalancePayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Serwer -> klient: historia transakcji (od najnowszej). */
	public record HistoryPayload(List<HistoryEntry> entries) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<HistoryPayload> TYPE = new CustomPacketPayload.Type<>(id("history"));
		private static final StreamCodec<ByteBuf, HistoryEntry> ENTRY = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, HistoryEntry::time,
				ByteBufCodecs.VAR_LONG, HistoryEntry::amount,
				ByteBufCodecs.STRING_UTF8, HistoryEntry::kind,
				ByteBufCodecs.STRING_UTF8, HistoryEntry::other,
				ByteBufCodecs.STRING_UTF8, HistoryEntry::title,
				HistoryEntry::new);
		public static final StreamCodec<RegistryFriendlyByteBuf, HistoryPayload> CODEC = StreamCodec.composite(
				ENTRY.apply(ByteBufCodecs.list(100)), HistoryPayload::entries,
				HistoryPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Serwer -> klient: wynik przelewu (komunikat w oknie banku). */
	public record ResultPayload(boolean ok, String message) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<ResultPayload> TYPE = new CustomPacketPayload.Type<>(id("result"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ResultPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, ResultPayload::ok,
				ByteBufCodecs.STRING_UTF8, ResultPayload::message,
				ResultPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Klient -> serwer: poproś o saldo i historię. */
	public record RequestSyncPayload() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<RequestSyncPayload> TYPE = new CustomPacketPayload.Type<>(id("request_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RequestSyncPayload> CODEC = StreamCodec.unit(new RequestSyncPayload());

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Klient -> serwer: przelew. */
	public record TransferPayload(String target, long amount, String title) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<TransferPayload> TYPE = new CustomPacketPayload.Type<>(id("transfer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TransferPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, TransferPayload::target,
				ByteBufCodecs.VAR_LONG, TransferPayload::amount,
				ByteBufCodecs.STRING_UTF8, TransferPayload::title,
				TransferPayload::new);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(BalancePayload.TYPE, BalancePayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HistoryPayload.TYPE, HistoryPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ResultPayload.TYPE, ResultPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RequestSyncPayload.TYPE, RequestSyncPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TransferPayload.TYPE, TransferPayload.CODEC);
	}
}
