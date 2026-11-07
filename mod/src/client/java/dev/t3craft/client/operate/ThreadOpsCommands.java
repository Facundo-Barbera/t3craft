package dev.t3craft.client.operate;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadOps;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * {@code /t3craft operate settle|snooze|pin|unpin|read <npc or env:thread> [duration]} (item 22).
 * The target is an NPC id ({@code env:thread}) or a thread title shown in the world; snooze takes
 * {@code 1h}, {@code 4h} or {@code tomorrow} as its last word (default 1h). Settle opens the
 * in-game confirmation.
 */
public final class ThreadOpsCommands {
	private ThreadOpsCommands() {
	}

	private static final SuggestionProvider<FabricClientCommandSource> NPCS = (c, b) -> {
		T3CraftClient.get().npcs().all().forEach(n -> b.suggest(n.id()));
		return b.buildFuture();
	};

	public static LiteralArgumentBuilder<FabricClientCommandSource> settle() {
		return simple("settle", t -> ThreadOpsUi.ops().settle(t));
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> pin() {
		return simple("pin", t -> ThreadOpsUi.ops().pin(t));
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> unpin() {
		return simple("unpin", t -> ThreadOpsUi.ops().unpin(t));
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> read() {
		return simple("read", t -> ThreadOpsUi.ops().markRead(t));
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> snooze() {
		return literal("snooze").then(argument("target", StringArgumentType.greedyString()).suggests(NPCS).executes(c -> {
			SnoozeArgs a = SnoozeArgs.parse(StringArgumentType.getString(c, "target"));
			return execute(c, a.target(), t -> ThreadOpsUi.ops().snooze(t, a.duration()));
		}));
	}

	/** {@code "<target> [1h|4h|tomorrow]"}: the last word is the duration when it parses as one. */
	record SnoozeArgs(String target, ThreadOps.Snooze duration) {
		static SnoozeArgs parse(String raw) {
			String s = raw.trim();
			int i = s.lastIndexOf(' ');
			if (i > 0) {
				ThreadOps.Snooze d = ThreadOps.Snooze.parse(s.substring(i + 1));
				if (d != null) {
					return new SnoozeArgs(s.substring(0, i).trim(), d);
				}
			}
			return new SnoozeArgs(s, ThreadOps.Snooze.HOUR_1);
		}
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> simple(String name, Function<ThreadOps.Target, OperateAction> build) {
		return literal(name).then(argument("target", StringArgumentType.greedyString()).suggests(NPCS)
				.executes(c -> execute(c, StringArgumentType.getString(c, "target"), build)));
	}

	private static int execute(CommandContext<FabricClientCommandSource> c, String raw, Function<ThreadOps.Target, OperateAction> build) {
		ThreadOps.Target t = resolve(T3CraftClient.get().npcs().all(), raw);
		if (t == null) {
			c.getSource().sendError(T3CraftCommands.prefix().append(Component.literal("no thread " + raw.trim() + " (use an NPC id env:thread or its title)")));
			return 0;
		}
		OperateAction a = build.apply(t);
		ThreadOpsUi.runFromCommand(a, r -> feedback(c.getSource(), a, r));
		return 1;
	}

	private static void feedback(FabricClientCommandSource src, OperateAction a, OperateResult r) {
		Component m = ThreadOpsUi.message(a, r);
		if (m == null) {
			m = Component.literal("cancelled");
		}
		src.sendFeedback(T3CraftCommands.prefix().append(m.copy().withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
	}

	/**
	 * NPC id first, then a unique title/label match (case-insensitive), then a bare {@code env:thread}
	 * that the world does not show (yet).
	 */
	static ThreadOps.@Nullable Target resolve(List<NpcInfo> npcs, String raw) {
		String q = raw.trim();
		if (q.isEmpty()) {
			return null;
		}
		for (NpcInfo n : npcs) {
			if (n.id().equals(q)) {
				return ThreadOps.Target.parse(n.id(), n.title());
			}
		}
		String lq = q.toLowerCase(Locale.ROOT);
		NpcInfo match = null;
		for (NpcInfo n : npcs) {
			if (n.title().toLowerCase(Locale.ROOT).equals(lq) || n.label().toLowerCase(Locale.ROOT).equals(lq)) {
				if (match != null) {
					return null; // ambiguous: ask for the id
				}
				match = n;
			}
		}
		if (match != null) {
			return ThreadOps.Target.parse(match.id(), match.title());
		}
		return q.contains(" ") ? null : ThreadOps.Target.parse(q, null);
	}
}
