package dev.t3craft.client.piece;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.piece.Piece;
import dev.t3craft.piece.PieceCatalog;
import dev.t3craft.piece.PieceScene;
import dev.t3craft.piece.PieceSpec;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * feat/piece-catalog-preview: look at one piece of the game at a time, at real scale, inside the T3Craft HQ world,
 * and change it without restarting the client. See {@code docs/features/piece-catalog-preview.md}.
 *
 * <p>The catalog ({@link PieceCatalog}) lists what exists in the code: parts of the citadel (the hub, the cloister, the
 * nodes, a barrio), the classic HQ, the kit, rooms of layout packs, and every variant of the citadel catalog. This class
 * is the client half: it builds the piece asked for on a worker thread (a part of the citadel needs the whole plan: seconds),
 * hands the stage ({@link PieceScene}) to {@link PieceStage}, which diffs it into the world on the server thread, and owns
 * the commands:
 *
 * <pre>
 * /t3craft piece                              what is on the stage, the groups
 * /t3craft piece list [group]                 the groups, or the pieces of one (clickable)
 * /t3craft piece show &lt;id&gt; [size] [front] [seed]   put a piece on the stage and go there
 * /t3craft piece next | prev                  the next / previous piece of the same group
 * /t3craft piece reload                       build the current piece again from the code, applying only what changed
 * /t3craft piece size &lt;min|mid|max|WxDxH&gt;     the same piece in another plot size
 * /t3craft piece front &lt;n|e|s|w&gt;              the same piece facing another side
 * /t3craft piece seed &lt;n&gt;                     the same piece with another seed
 * /t3craft piece go | back | clear            to the stage / back where you came from / take everything off
 * </pre>
 * It only works in the HQ world (anywhere else it says so and does nothing).
 */
public final class PieceClient {
	private static final int HINT_DELAY_TICKS = 160;
	private static final PieceStage STAGE = PieceStage.get();
	private static final AtomicInteger GENERATION = new AtomicInteger();

	private static boolean registered;
	private static @Nullable ExecutorService worker;
	private static volatile @Nullable PieceSpec current;
	private static int serverFailures;
	// client thread only
	private static int worldTicks;
	private static boolean hinted;

	private PieceClient() {
	}

	/** Client init (idempotent). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				STAGE.onServerStarted(server);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("[piece] could not start for this world", e);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> STAGE.onServerStopping());
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (serverFailures >= 3) {
				return;
			}
			try {
				STAGE.tick(server);
			} catch (RuntimeException e) {
				serverFailures++;
				T3Craft.LOGGER.error("[piece] stage tick failed ({}/3)", serverFailures, e);
			}
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			ExecutorService w = worker;
			if (w != null) {
				w.shutdownNow();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("piece", PieceClient::tick));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(literal("t3craft").then(tree())));
	}

	// ------------------------------------------------------------------ commands

	private static LiteralArgumentBuilder<FabricClientCommandSource> tree() {
		return literal("piece")
				.executes(c -> status(c.getSource()))
				.then(literal("list").executes(c -> list(c.getSource(), null))
						.then(argument("group", StringArgumentType.word()).suggests((c, b) -> suggest(PieceCatalog.groups(), b))
								.executes(c -> list(c.getSource(), StringArgumentType.getString(c, "group")))))
				.then(literal("show").then(argument("id", StringArgumentType.word()).suggests((c, b) -> suggest(PieceCatalog.ids(), b))
						.executes(c -> show(c.getSource(), PieceSpec.of(StringArgumentType.getString(c, "id")), true, false))
						.then(argument("size", StringArgumentType.word()).suggests((c, b) -> suggest(List.of("min", "mid", "max"), b))
								.executes(c -> showSized(c.getSource(), StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "size"), null, null))
								.then(argument("front", StringArgumentType.word()).suggests((c, b) -> suggest(List.of("n", "e", "s", "w"), b))
										.executes(c -> showSized(c.getSource(), StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "size"),
												StringArgumentType.getString(c, "front"), null))
										.then(argument("seed", LongArgumentType.longArg(0))
												.executes(c -> showSized(c.getSource(), StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "size"),
														StringArgumentType.getString(c, "front"), LongArgumentType.getLong(c, "seed"))))))))
				.then(literal("next").executes(c -> step(c.getSource(), 1)))
				.then(literal("prev").executes(c -> step(c.getSource(), -1)))
				.then(literal("reload").executes(c -> reload(c.getSource())))
				.then(literal("size").then(argument("size", StringArgumentType.word()).suggests((c, b) -> suggest(List.of("min", "mid", "max"), b))
						.executes(c -> change(c.getSource(), StringArgumentType.getString(c, "size"), null, null))))
				.then(literal("front").then(argument("front", StringArgumentType.word()).suggests((c, b) -> suggest(List.of("n", "e", "s", "w"), b))
						.executes(c -> change(c.getSource(), null, StringArgumentType.getString(c, "front"), null))))
				.then(literal("seed").then(argument("seed", LongArgumentType.longArg(0))
						.executes(c -> change(c.getSource(), null, null, LongArgumentType.getLong(c, "seed")))))
				.then(literal("go").executes(c -> onServer(c.getSource(), (s, server, id) -> s.go(server, id))))
				.then(literal("back").executes(c -> onServer(c.getSource(), (s, server, id) -> s.back(server, id))))
				.then(literal("clear").executes(c -> clear(c.getSource())));
	}

	private static CompletableFuture<Suggestions> suggest(Iterable<String> options, SuggestionsBuilder b) {
		String typed = b.getRemainingLowerCase();
		for (String o : options) {
			if (o.toLowerCase(Locale.ROOT).startsWith(typed)) {
				b.suggest(o);
			}
		}
		return b.buildFuture();
	}

	private static int showSized(FabricClientCommandSource src, String id, String size, @Nullable String front, @Nullable Long seed) {
		if (!PieceSpec.validSize(size)) {
			src.sendError(prefix().append(Component.literal("size is min, mid, max or WxDxH (frontage x depth x height, up to 200), not " + size)));
			return 0;
		}
		PieceSpec spec = PieceSpec.of(id).withSize(size.toLowerCase(Locale.ROOT));
		if (front != null) {
			net.minecraft.core.Direction d = PieceSpec.parseFront(front);
			if (d == null) {
				src.sendError(prefix().append(Component.literal("front is n, e, s or w, not " + front)));
				return 0;
			}
			spec = spec.withFront(d);
		}
		if (seed != null) {
			spec = spec.withSeed(seed);
		}
		return show(src, spec, true, false);
	}

	/** {@code size} / {@code front} / {@code seed}: the current piece with one thing changed. */
	private static int change(FabricClientCommandSource src, @Nullable String size, @Nullable String front, @Nullable Long seed) {
		PieceSpec c = current;
		if (c == null) {
			src.sendError(prefix().append(Component.literal("nothing on the stage yet: /t3craft piece show <id>")));
			return 0;
		}
		PieceSpec next = c;
		if (size != null) {
			if (!PieceSpec.validSize(size)) {
				src.sendError(prefix().append(Component.literal("size is min, mid, max or WxDxH (frontage x depth x height, up to 200), not " + size)));
				return 0;
			}
			next = next.withSize(size.toLowerCase(Locale.ROOT));
		}
		if (front != null) {
			net.minecraft.core.Direction d = PieceSpec.parseFront(front);
			if (d == null) {
				src.sendError(prefix().append(Component.literal("front is n, e, s or w, not " + front)));
				return 0;
			}
			next = next.withFront(d);
		}
		if (seed != null) {
			next = next.withSeed(seed);
		}
		return show(src, next, true, false);
	}

	private static int step(FabricClientCommandSource src, int dir) {
		PieceSpec c = current;
		Piece from = c == null ? null : PieceCatalog.byId(c.id());
		if (from == null) {
			List<Piece> hq = PieceCatalog.group("hq");
			from = hq.get(dir > 0 ? hq.size() - 1 : 0); // so that "next" from nothing is the first, "prev" the last
		}
		Piece to = PieceCatalog.step(from, dir);
		if (to == null) {
			return 0;
		}
		PieceSpec base = c == null ? PieceSpec.of(to.id()) : c.withId(to.id());
		String size = base.size();
		boolean keyword = size != null && PieceSpec.box(size) == null;
		return show(src, keyword ? base : base.withSize(null), true, false);
	}

	private static int reload(FabricClientCommandSource src) {
		PieceSpec c = current;
		if (c == null) {
			src.sendError(prefix().append(Component.literal("nothing on the stage yet: /t3craft piece show <id>")));
			return 0;
		}
		return show(src, c, false, true);
	}

	private static int clear(FabricClientCommandSource src) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !STAGE.active()) {
			src.sendError(prefix().append(Component.literal("the piece stage lives in the T3Craft HQ world (title screen > T3Craft HQ)")));
			return 0;
		}
		GENERATION.incrementAndGet();
		current = null;
		STAGE.clear(mc.player.getUUID());
		src.sendFeedback(line("clearing the stage"));
		return 1;
	}

	private interface ServerAction {
		void run(PieceStage stage, MinecraftServer server, UUID player);
	}

	/** Runs {@code action} on the integrated server's thread for the local player; only in the HQ world. */
	private static int onServer(FabricClientCommandSource src, ServerAction action) {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null || !STAGE.active()) {
			src.sendError(prefix().append(Component.literal("the piece stage lives in the T3Craft HQ world (title screen > T3Craft HQ)")));
			return 0;
		}
		UUID id = mc.player.getUUID();
		server.execute(() -> {
			try {
				action.run(STAGE, server, id);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("[piece] command failed", e);
			}
		});
		return 1;
	}

	// ------------------------------------------------------------------ show

	private static int show(FabricClientCommandSource src, PieceSpec spec, boolean go, boolean fresh) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !STAGE.active()) {
			src.sendError(prefix().append(Component.literal("the piece stage lives in the T3Craft HQ world (title screen > T3Craft HQ)")));
			return 0;
		}
		Piece piece = PieceCatalog.byId(spec.id());
		if (piece == null) {
			src.sendError(prefix().append(Component.literal("no piece '" + spec.id() + "': /t3craft piece list")));
			return 0;
		}
		PieceSpec use = spec.withId(piece.id());
		current = use;
		UUID player = mc.player.getUUID();
		int gen = GENERATION.incrementAndGet();
		src.sendFeedback(line((fresh ? "building again: " : "building: ") + piece.title() + (piece.parametric() ? " (" + use.describe() + ")" : "")
				+ (isBig(piece) ? " - a part of the citadel takes a few seconds" : "")));
		executor().execute(() -> build(mc, piece, use, go, fresh, player, gen));
		return 1;
	}

	private static boolean isBig(Piece p) {
		return p.group().equals("hq") || p.group().equals("classic");
	}

	/** Worker thread. */
	private static void build(Minecraft mc, Piece piece, PieceSpec spec, boolean go, boolean fresh, UUID player, int gen) {
		if (gen != GENERATION.get()) {
			return; // another piece was asked for meanwhile
		}
		long t0 = System.nanoTime();
		try {
			Piece.Built built = piece.build(spec, fresh);
			if (gen != GENERATION.get()) {
				return;
			}
			PieceScene scene = PieceScene.compose(piece.id(), piece.title(), built.detail(), built.model());
			STAGE.offer(scene, player, go);
			long ms = (System.nanoTime() - t0) / 1_000_000L;
			T3Craft.LOGGER.info("[piece] built {} ({}): {} block(s), {} ms", piece.id(), built.detail(), built.model().solid(), ms);
			String dims = built.model().sizeX() + "x" + built.model().sizeY() + "x" + built.model().sizeZ();
			mc.execute(() -> ready(mc, piece, spec, dims, built.model().solid(), built.detail(), ms));
		} catch (Exception | LinkageError e) {
			T3Craft.LOGGER.error("[piece] could not build {}", piece.id(), e);
			String why = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			mc.execute(() -> {
				if (mc.player != null) {
					mc.player.sendSystemMessage(prefix().append(Component.literal(piece.id() + ": could not build it: " + why).withStyle(ChatFormatting.RED)));
				}
			});
		}
	}

	/** Client thread. */
	private static void ready(Minecraft mc, Piece piece, PieceSpec spec, String dims, int blocks, String detail, long ms) {
		if (mc.player == null) {
			return;
		}
		MutableComponent msg = prefix().append(Component.literal(piece.id() + ": " + dims + " (WxHxD), " + blocks + " blocks, built in " + ms + " ms. ")
				.withStyle(ChatFormatting.GRAY));
		msg.append(button("[prev]", "t3craft piece prev", "the previous piece of the " + piece.group() + " group")).append(" ");
		msg.append(button("[next]", "t3craft piece next", "the next piece of the " + piece.group() + " group")).append(" ");
		msg.append(button("[reload]", "t3craft piece reload", "build it again from the code and apply what changed")).append(" ");
		msg.append(button("[back]", "t3craft piece back", "back where you came from"));
		mc.player.sendSystemMessage(msg);
		mc.player.sendSystemMessage(line("  " + detail + (piece.parametric() ? "; /t3craft piece size|front|seed change it" : "")));
	}

	// ------------------------------------------------------------------ listing

	private static int status(FabricClientCommandSource src) {
		Map<String, Integer> counts = PieceCatalog.counts();
		int total = 0;
		for (int n : counts.values()) {
			total += n;
		}
		src.sendFeedback(line("Piece catalog: " + total + " pieces in " + counts.size() + " groups. Real scale, in the HQ world, no restart."));
		PieceStage.Status st = STAGE.status();
		if (!st.inHq()) {
			src.sendFeedback(line("open the T3Craft HQ world to see them (title screen > T3Craft HQ)"));
		} else {
			PieceSpec c = current;
			src.sendFeedback(line("stage at " + (st.stage() == null ? "?" : st.stage().toShortString()) + ": "
					+ (st.shown() == null ? "empty" : st.shown() + (st.building() ? " (" + st.percent() + "% built)" : ""))
					+ (c == null ? "" : "; asked for " + c.describe())));
		}
		MutableComponent groups = line("groups: ");
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			groups.append(button("[" + e.getKey() + " " + e.getValue() + "]", "t3craft piece list " + e.getKey(), "list the " + e.getKey() + " pieces")).append(" ");
		}
		src.sendFeedback(groups);
		src.sendFeedback(line("/t3craft piece show <id> [size] [front] [seed] | next | prev | reload | size | front | seed | go | back | clear"));
		return 1;
	}

	private static int list(FabricClientCommandSource src, @Nullable String group) {
		if (group == null) {
			return status(src);
		}
		List<Piece> pieces = PieceCatalog.group(group);
		if (pieces.isEmpty()) {
			src.sendError(prefix().append(Component.literal("no group '" + group + "' (groups: " + String.join(", ", PieceCatalog.groups()) + ")")));
			return 0;
		}
		src.sendFeedback(line(group + ": " + pieces.size() + " piece(s)" + (group.equals("pack") ? " (the rooms of the layout packs active now)" : "")));
		for (Piece p : pieces) {
			src.sendFeedback(Component.literal("  ").append(button(p.id(), "t3craft piece show " + p.id(), p.note()))
					.append(Component.literal("  " + p.title()).withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	// ------------------------------------------------------------------ worker, hint, messages

	private static synchronized ExecutorService executor() {
		ExecutorService w = worker;
		if (w == null) {
			w = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "t3craft-piece");
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);
				return t;
			});
			worker = w;
		}
		return w;
	}

	/** Once per visit to the HQ world: that the catalog exists. */
	private static void tick(Minecraft mc) {
		if (mc.player == null || !STAGE.active()) {
			worldTicks = 0;
			hinted = false;
			return;
		}
		if (hinted || ++worldTicks < HINT_DELAY_TICKS) {
			return;
		}
		hinted = true;
		MutableComponent msg = prefix().append(Component.literal("Piece catalog: " + PieceCatalog.all().size() + " pieces of the game, at real scale. ")
				.withStyle(ChatFormatting.GRAY));
		msg.append(button("[Hub]", "t3craft piece show hq_hub", "the central building of the HQ, on its own stage")).append(" ");
		msg.append(button("[Catalog]", "t3craft piece", "groups and commands"));
		mc.player.sendSystemMessage(msg);
	}

	private static MutableComponent button(String label, String command, String hover) {
		return Component.literal(label).withStyle(s -> s.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand(command))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
	}

	private static MutableComponent prefix() {
		return T3CraftCommands.prefix();
	}

	private static MutableComponent line(String text) {
		return prefix().append(Component.literal(text).withStyle(ChatFormatting.GRAY));
	}
}
