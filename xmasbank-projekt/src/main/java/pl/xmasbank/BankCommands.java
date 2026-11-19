package pl.xmasbank;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** /wypłać <kwota>  i  /wpłać <kwota|wszystko>  (z aliasami bez polskich znaków). */
public final class BankCommands {
	private BankCommands() {}

	private static final long MAX_WITHDRAW = 100_000;

	public static void register(CommandDispatcher<CommandSourceStack> d) {
		for (String name : new String[] {"wypłać", "wyplac"}) {
			d.register(Commands.literal(name)
					.then(Commands.argument("kwota", LongArgumentType.longArg(1, MAX_WITHDRAW))
							.executes(ctx -> withdraw(ctx, LongArgumentType.getLong(ctx, "kwota")))));
		}
		for (String name : new String[] {"wpłać", "wplac"}) {
			LiteralArgumentBuilder<CommandSourceStack> b = Commands.literal(name);
			b.then(Commands.argument("kwota", LongArgumentType.longArg(1, MAX_WITHDRAW))
					.executes(ctx -> deposit(ctx, LongArgumentType.getLong(ctx, "kwota"))));
			b.then(Commands.literal("wszystko").executes(ctx -> deposit(ctx, -1)));
			d.register(b);
		}
	}

	private static int countCoins(Inventory inv) {
		int total = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(XmasBank.COIN)) {
				total += s.getCount();
			}
		}
		return total;
	}

	private static int withdraw(CommandContext<CommandSourceStack> ctx, long amount) throws CommandSyntaxException {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer p = src.getPlayerOrException();
		if (Bank.balance(p) < amount) {
			src.sendFailure(Component.literal("Za mało środków na koncie (masz " + Bank.balance(p) + ")."));
			return 0;
		}
		Bank.apply(p, -amount, "sys", "", "Wypłata " + amount, "");
		long left = amount;
		while (left > 0) {
			int n = (int) Math.min(left, 64);
			ItemStack stack = new ItemStack(XmasBank.COIN, n);
			if (!p.getInventory().add(stack) || !stack.isEmpty()) {
				p.drop(stack, false);
			}
			left -= n;
		}
		src.sendSuccess(() -> Component.literal("§a[Bank] §fWypłacono §e" + amount + " §fmonet do ekwipunku."), false);
		return 1;
	}

	private static int deposit(CommandContext<CommandSourceStack> ctx, long requested) throws CommandSyntaxException {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer p = src.getPlayerOrException();
		Inventory inv = p.getInventory();
		int have = countCoins(inv);
		long amount = requested < 0 ? have : requested;
		if (amount <= 0 || have < amount) {
			src.sendFailure(Component.literal("Nie masz tylu monet w ekwipunku (masz " + have + ")."));
			return 0;
		}
		long left = amount;
		for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(XmasBank.COIN)) {
				int take = (int) Math.min(left, s.getCount());
				s.shrink(take);
				left -= take;
			}
		}
		Bank.apply(p, amount, "sys", "", "Wpłata " + amount, "");
		final long done = amount;
		src.sendSuccess(() -> Component.literal("§a[Bank] §fWpłacono §e" + done + " §fmonet na konto."), false);
		return 1;
	}
}
