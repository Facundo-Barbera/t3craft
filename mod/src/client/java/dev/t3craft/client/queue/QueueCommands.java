package dev.t3craft.client.queue;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * <ul>
 *   <li>{@code /t3craft queue}: opens {@link QueueScreen} (every environment).</li>
 *   <li>{@code /t3craft queue <env:thread>}: that thread's queue in chat (read-only).</li>
 *   <li>{@code /t3craft operate queue cancel <env:thread> <run> [confirm]}: cancel a queued run.
 *   Destructive: without {@code confirm} it opens the in-game confirmation.</li>
 *   <li>{@code /t3craft operate queue up|down <env:thread> <run>}: move it one place.</li>
 * </ul>
 * {@code <run>} is a run id or a unique prefix of one (the listing shows 8 characters).
 */
public final class QueueCommands {
	static final long LOAD_TIMEOUT_MS = 5_000;
	static final int SHORT_ID = 8;

	private QueueCommands() {
	}

	/** {@code env:thread} (one token up to the next space; {@code :} is not a word character in brigadier). */
	static final ArgumentType<String> THREAD = new ArgumentType<>() {
		@Override
		public String parse(StringReader r) {
			int start = r.getCursor();
			while (r.canRead() && r.peek() != ' ') {
				r.skip();
			}
			return r.getString().substring(start, r.getCursor());
		}
	};

	private static SuggestionProvider<FabricClientCommandSource> threads(T3CraftClient client) {
		return (c, b) -> {
			java.util.Set<String> keys = new java.util.LinkedHashSet<>();
			QueueClient.store().all().forEach(s -> keys.add(s.key().toString()));
			QueueTargets.working(QueueClient.views(client), QueueTargets.SCREEN_CAP).forEach(k -> keys.add(k.toString()));
			keys.stream().filter(k -> k.startsWith(b.getRemaining())).forEach(b::suggest);
			return b.buildFuture();
		};
	}

	private static SuggestionProvider<FabricClientCommandSource> runs() {
		return (c, b) -> {
			QueueStore.Key k = QueueStore.Key.parse(c.getArgument("thread", String.class));
			QueueClient.store().get(k.envId(), k.threadId()).runs().forEach(r -> b.suggest(r.id()));
			return b.buildFuture();
		};
	}

	public static void build(CommandDispatcher<FabricClientCommandSource> d, T3CraftClient client) {
		d.register(literal("t3craft").then(literal("queue")
				.executes(c -> {
					client.openNextTick(new QueueScreen(null, (QueueStore.Key) null));
					return 1;
				})
				.then(argument("thread", THREAD).suggests(threads(client)).executes(c -> list(c, client)))));
		d.register(literal("t3craft").then(literal("operate").then(operate(client))));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> operate(T3CraftClient client) {
		return literal("queue")
				.then(literal("cancel").then(argument("thread", THREAD).suggests(threads(client))
						.then(argument("run", StringArgumentType.word()).suggests(runs())
								.executes(c -> cancel(c, client, false))
								.then(literal("confirm").executes(c -> cancel(c, client, true))))))
				.then(literal("up").then(argument("thread", THREAD).suggests(threads(client))
						.then(argument("run", StringArgumentType.word()).suggests(runs()).executes(c -> move(c, client, QueueOps.Direction.UP)))))
				.then(literal("down").then(argument("thread", THREAD).suggests(threads(client))
						.then(argument("run", StringArgumentType.word()).suggests(runs()).executes(c -> move(c, client, QueueOps.Direction.DOWN)))));
	}

	// ---- read --------------------------------------------------------------------------------

	private static int list(CommandContext<FabricClientCommandSource> c, T3CraftClient client) {
		QueueStore.Key k = key(c);
		if (k == null) {
			return 0;
		}
		load(c, k, s -> {
			String title = QueueTargets.title(QueueClient.views(client), k.envId(), k.threadId());
			c.getSource().sendFeedback(T3CraftCommands.prefix().append(Component.translatable("t3craft.queue.cmd.header",
					Component.literal(k.toString()).withStyle(ChatFormatting.AQUA),
					Component.literal(title == null ? "-" : title).withStyle(ChatFormatting.WHITE), s.runs().size())));
			if (s.runs().isEmpty()) {
				c.getSource().sendFeedback(Component.literal("  ").append(Component.translatable("t3craft.queue.empty").withStyle(ChatFormatting.GRAY)));
			}
			int i = 0;
			for (QueuedRun r : s.runs()) {
				c.getSource().sendFeedback(line(++i, k, r));
			}
		});
		return 1;
	}

	private static Component line(int n, QueueStore.Key k, QueuedRun r) {
		String id = r.id().length() > SHORT_ID ? r.id().substring(0, SHORT_ID) : r.id();
		MutableComponent l = Component.literal("  " + n + ". ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(id).withStyle(s -> s.withColor(ChatFormatting.YELLOW)
						.withClickEvent(new ClickEvent.SuggestCommand("/t3craft operate queue cancel " + k + " " + r.id()))
						.withHoverEvent(new HoverEvent.ShowText(Component.literal(r.id())))))
				.append(Component.literal("  " + (r.preview() == null ? "…" : r.preview())).withStyle(ChatFormatting.WHITE));
		if (r.held()) {
			l.append(Component.literal("  ")).append(Component.translatable("t3craft.queue.held").withStyle(ChatFormatting.GOLD));
		}
		if (r.automatic()) {
			l.append(Component.literal("  ")).append(Component.translatable("t3craft.queue.automatic").withStyle(ChatFormatting.DARK_AQUA));
		}
		return l;
	}

	// ---- write -------------------------------------------------------------------------------

	private static int cancel(CommandContext<FabricClientCommandSource> c, T3CraftClient client, boolean confirmed) {
		QueueStore.Key k = key(c);
		if (k == null) {
			return 0;
		}
		String runArg = StringArgumentType.getString(c, "run");
		load(c, k, s -> {
			QueuedRun r = QueueOps.find(s.runs(), runArg);
			if (r == null) {
				notQueued(c, runArg);
				return;
			}
			String title = QueueTargets.title(QueueClient.views(client), k.envId(), k.threadId());
			if (confirmed) {
				QueueClient.ops().cancel(r, title, OperateGate.Origin.COMMAND, true).thenAccept(res -> report(c, res));
			} else {
				// Destructive: the same in-game confirmation as the screen (opened next tick, after the chat closes).
				OperateAction a = QueueClient.ops().cancelAction(r, title);
				QueueClient.nextTick(() -> OperateUi.run(a, OperateGate.Origin.COMMAND, null, res -> reportNow(c, res)));
			}
		});
		return 1;
	}

	private static int move(CommandContext<FabricClientCommandSource> c, T3CraftClient client, QueueOps.Direction dir) {
		QueueStore.Key k = key(c);
		if (k == null) {
			return 0;
		}
		String runArg = StringArgumentType.getString(c, "run");
		load(c, k, s -> {
			QueuedRun r = QueueOps.find(s.runs(), runArg);
			if (r == null) {
				notQueued(c, runArg);
				return;
			}
			String title = QueueTargets.title(QueueClient.views(client), k.envId(), k.threadId());
			QueueClient.ops().move(s.runs(), r, dir, title, OperateGate.Origin.COMMAND).thenAccept(res -> report(c, res));
		});
		return 1;
	}

	// ---- helpers -----------------------------------------------------------------------------

	private static QueueStore.@org.jspecify.annotations.Nullable Key key(CommandContext<FabricClientCommandSource> c) {
		QueueStore.Key k = QueueStore.Key.parse(c.getArgument("thread", String.class));
		if (k.envId().isEmpty() || k.threadId().isEmpty()) {
			c.getSource().sendError(T3CraftCommands.prefix().append(Component.translatable("t3craft.queue.cmd.usage")));
			return null;
		}
		return k;
	}

	/** Loads the queue, then runs {@code then} on the client thread when it is known; reports otherwise. */
	private static void load(CommandContext<FabricClientCommandSource> c, QueueStore.Key k, Consumer<QueueStore.Snapshot> then) {
		if (!QueueClient.readable()) {
			c.getSource().sendError(T3CraftCommands.prefix().append(Component.translatable("t3craft.queue.unavailable",
					QueueClient.source().unavailableReason())));
			return;
		}
		QueueClient.store().load(k.envId(), k.threadId(), LOAD_TIMEOUT_MS).thenAccept(s -> Minecraft.getInstance().execute(() -> {
			if (s.known()) {
				then.accept(s);
			} else {
				c.getSource().sendError(T3CraftCommands.prefix().append(Component.translatable("t3craft.queue.unknown",
						k.toString(), s.reason() == null ? s.status().name().toLowerCase() : s.reason())));
			}
		}));
	}

	private static void notQueued(CommandContext<FabricClientCommandSource> c, String run) {
		c.getSource().sendError(T3CraftCommands.prefix().append(Component.translatable("t3craft.queue.cmd.no_run", run)));
	}

	private static void report(CommandContext<FabricClientCommandSource> c, OperateResult r) {
		Minecraft.getInstance().execute(() -> reportNow(c, r));
	}

	private static void reportNow(CommandContext<FabricClientCommandSource> c, OperateResult r) {
		c.getSource().sendFeedback(T3CraftCommands.prefix().append(resultLine(r)));
	}

	/** Player-facing result line of a queue write, for the chat. */
	static Component resultLine(OperateResult r) {
		ChatFormatting color = switch (r.outcome()) {
			case OK -> ChatFormatting.GREEN;
			case CANCELLED, READ_ONLY -> ChatFormatting.GRAY;
			case FAILED -> ChatFormatting.GOLD;
			default -> ChatFormatting.RED;
		};
		return Component.translatable("t3craft.queue.result." + r.outcome().wire, r.message()).withStyle(color);
	}
}
