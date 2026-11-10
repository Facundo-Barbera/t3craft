package dev.t3craft.client.islands;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.debug.GuardedEvents;
import dev.t3craft.client.demo.VanillaDemoWorld;
import dev.t3craft.build.Placement;
import dev.t3craft.mapgen.MapTypes;
import dev.t3craft.mapgen.islands.Bridge;
import dev.t3craft.mapgen.islands.BridgePlanner;
import dev.t3craft.mapgen.islands.Island;
import dev.t3craft.mapgen.islands.IslandField;
import dev.t3craft.mapgen.islands.IslandMix;
import dev.t3craft.mapgen.islands.bridge.BridgeNetwork;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Map type "floating islands" (feat/algo-map-generators, feat/islands-worldgen): its own world (save folder
 * {@value IslandsWorld#FOLDER}, a void world like the HQ's) whose islands and bridges are built at run time by
 * {@link IslandsRuntime}. The HQ world stays the default and is never touched.
 *
 * <p>Commands (merged under {@code /t3craft}):
 * <ul>
 *   <li>{@code islands}: creates the world the first time and opens it afterwards (from another world it asks
 *       for {@code islands confirm}, which saves and leaves that one);</li>
 *   <li>{@code islands new [seed] [mix]}: a field with that seed (a number, any word, or {@code random}; random
 *       when left out) and size mix ({@code S40,M30,L20,XG10} or a preset, see {@link IslandMix#PRESETS}; the
 *       default mix when left out). Creates the world when there is none; otherwise regenerates the islands
 *       world in place with the new settings (opening it first, saving and leaving another world without asking:
 *       the command is the confirmation);</li>
 *   <li>{@code islands regen}: in the islands world, clears every island and bridge and builds the field again
 *       with the world's own seed and mix (after a code change, or to undo edits);</li>
 *   <li>{@code islands status}, {@code map list}, {@code map open floating-islands}.</li>
 * </ul>
 */
public final class IslandsClient implements ClientModInitializer {
	private enum Pending { NONE, OPEN, OPEN_CONFIRMED }

	/** Seed and mix for a world to be created or regenerated. */
	record Settings(long seed, IslandMix mix) {
	}

	private volatile Pending pending = Pending.NONE;
	/** Set by {@code islands new} before the world is opened; read on the server thread when it has started. */
	private static volatile @Nullable Settings pendingNew;

	@Override
	public void onInitializeClient() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!VanillaDemoWorld.isDemoServer(server, IslandsWorld.FOLDER)) {
				pendingNew = null; // an "islands new" whose world never opened
				Regen.cancel();
				IslandsRuntime.stop();
				return;
			}
			try {
				started(server);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("[islands] could not start, the islands world stays empty this session", e);
				Regen.cancel();
				IslandsRuntime.stop();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Regen.cancel();
			IslandsRuntime.stop();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			try {
				Regen.tick(server);
				IslandsRuntime.tick(server);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("[islands] tick failed, turned off for this session", e);
				Regen.cancel();
				IslandsRuntime.stop();
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(GuardedEvents.endClientTick("islands", this::tick));
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(literal("t3craft")
				.then(literal("islands")
						// The chat screen closes right after a command runs: act on the next tick.
						.executes(c -> queue(Pending.OPEN))
						.then(literal("confirm").executes(c -> queue(Pending.OPEN_CONFIRMED)))
						.then(literal("status").executes(c -> {
							say("islands: " + status());
							return 1;
						}))
						.then(literal("regen").executes(c -> regen()))
						.then(literal("new")
								.executes(c -> fresh(null, null))
								.then(argument("seed", StringArgumentType.word())
										.executes(c -> fresh(StringArgumentType.getString(c, "seed"), null))
										.then(argument("mix", StringArgumentType.greedyString())
												.executes(c -> fresh(StringArgumentType.getString(c, "seed"), StringArgumentType.getString(c, "mix")))))))
				.then(literal("map")
						.executes(c -> {
							list();
							return 1;
						})
						.then(literal("list").executes(c -> {
							list();
							return 1;
						}))
						.then(literal("open").then(literal(MapTypes.FLOATING_ISLANDS.id()).executes(c -> queue(Pending.OPEN)))))));
	}

	/** Server thread, the islands world's server is up: settings first, then the runtime, then a pending regeneration. */
	private static void started(MinecraftServer server) {
		Path root = VanillaDemoWorld.worldRoot(server);
		IslandsState st = IslandsState.load(root);
		Settings asked = pendingNew;
		pendingNew = null;
		if (!st.hasSettings()) {
			Settings s = asked != null ? asked : new Settings(IslandsWorld.DEFAULT_SEED, IslandMix.DEFAULT);
			st.settings(s.seed(), s.mix());
			st.saveIfDirty();
			asked = null; // a new world: created with these settings, nothing to regenerate
		}
		IslandsWorld.use(st.seed(), st.mix());
		T3Craft.LOGGER.info("[islands] seed {}, mix {}, cell {}", st.seed(), st.mix().text(), IslandField.cellFor(st.mix()));
		IslandsRuntime.start(server, root);
		if (asked != null && (asked.seed() != st.seed() || !asked.mix().equals(st.mix()))) {
			Regen.begin(server, asked);
		}
	}

	private int queue(Pending p) {
		pending = p;
		return 1;
	}

	private void tick(Minecraft mc) {
		Pending p = pending;
		pending = Pending.NONE;
		if (p != Pending.NONE) {
			open(mc, p == Pending.OPEN_CONFIRMED, null);
		}
	}

	/** The integrated server when it runs the islands world, else null. */
	private static @Nullable MinecraftServer islandsServer(Minecraft mc) {
		MinecraftServer s = mc.getSingleplayerServer();
		return s != null && VanillaDemoWorld.isDemoServer(s, IslandsWorld.FOLDER) ? s : null;
	}

	/**
	 * Creates the islands world the first time and opens it afterwards: what {@code /t3craft islands} does, also
	 * called by the HQ menu's map type selector. {@code back} is the screen to return to if vanilla cancels the
	 * load (null: the title screen).
	 */
	public static void open(Minecraft mc, boolean confirmed, @Nullable Screen back) {
		Screen origin = back != null ? back : new TitleScreen();
		boolean inWorld = mc.level != null;
		if (inWorld && islandsServer(mc) != null) {
			say("you are already in " + IslandsWorld.NAME);
			return;
		}
		if (inWorld && !confirmed) {
			say("you are in another world: /t3craft islands confirm saves and leaves it, then opens " + IslandsWorld.NAME);
			return;
		}
		if (inWorld) {
			VanillaDemoWorld.leaveWorld(mc); // blocks until the integrated server has stopped
		}
		boolean exists = VanillaDemoWorld.exists(mc, IslandsWorld.FOLDER);
		T3Craft.LOGGER.info("[islands] {} '{}'", exists ? "OPEN" : "CREATE", IslandsWorld.FOLDER);
		if (exists) {
			VanillaDemoWorld.open(mc, IslandsWorld.FOLDER, origin);
		} else {
			Settings s = pendingNew;
			VanillaDemoWorld.createVoidWorld(mc, IslandsWorld.FOLDER, IslandsWorld.NAME, s != null ? s.seed() : IslandsWorld.DEFAULT_SEED, origin);
		}
	}

	/** {@code islands new [seed] [mix]}. */
	private int fresh(@Nullable String seedText, @Nullable String mixText) {
		IslandMix mix = mixText == null ? IslandMix.DEFAULT : IslandMix.parse(mixText);
		if (mix == null) {
			say("unknown size mix '" + mixText + "': write it like S40,M30,L20,XG10 (sizes S, M, L, XG) or use a preset: " + IslandMix.presetNames());
			return 0;
		}
		Settings s = new Settings(parseSeed(seedText), mix);
		Minecraft mc = Minecraft.getInstance();
		MinecraftServer server = islandsServer(mc);
		if (server != null) {
			say("new field: seed " + s.seed() + ", mix " + mix.describe() + "; clearing the old islands");
			server.execute(() -> Regen.begin(server, s));
			return 1;
		}
		pendingNew = s;
		say((VanillaDemoWorld.exists(mc, IslandsWorld.FOLDER) ? "regenerating " : "creating ") + IslandsWorld.NAME + " with seed " + s.seed() + ", mix "
				+ mix.describe());
		return queue(Pending.OPEN_CONFIRMED);
	}

	/** {@code islands regen}. */
	private int regen() {
		MinecraftServer server = islandsServer(Minecraft.getInstance());
		if (server == null) {
			say("open " + IslandsWorld.NAME + " first (/t3craft islands); /t3craft islands new [seed] [mix] makes a new field from anywhere");
			return 0;
		}
		Settings s = new Settings(IslandsWorld.SEED, IslandsWorld.mix());
		say("regenerating the field (seed " + s.seed() + ", mix " + s.mix().describe() + ")");
		server.execute(() -> Regen.begin(server, s));
		return 1;
	}

	/** A seed from text: a number as is, {@code random} (or nothing) a random one, any other word its hash. */
	static long parseSeed(@Nullable String text) {
		if (text == null || text.isBlank() || text.equalsIgnoreCase("random")) {
			return ThreadLocalRandom.current().nextLong();
		}
		try {
			return Long.parseLong(text.trim());
		} catch (NumberFormatException e) {
			return text.trim().hashCode();
		}
	}

	private static String status() {
		String regen = Regen.describe();
		if (regen != null) {
			return regen;
		}
		String base = IslandsRuntime.describe();
		if (!IslandsRuntime.running()) {
			return base + "; size mixes: " + IslandMix.presetNames();
		}
		IslandMix m = IslandsWorld.mix();
		return base + "; seed " + IslandsWorld.SEED + ", mix " + m.describe() + ", cell " + IslandField.cellFor(m) + " blocks";
	}

	private static void list() {
		for (MapTypes t : MapTypes.all()) {
			say(t.name() + " (" + t.id() + "): " + t.summary() + " Open with " + t.openWith());
		}
	}

	/** Chat text in the theme's text colour. */
	static Component text(String text) {
		return Component.literal(text).withColor(ChatTheme.warmStudio().text() & 0xFFFFFF);
	}

	private static void say(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(T3CraftCommands.prefix().append(text(text)));
		} else {
			T3Craft.LOGGER.info("[islands] {}", text);
		}
	}

	// ------------------------------------------------------------------ regeneration

	/**
	 * Regenerates the islands world in place, on the server thread: stops the runtime, clears every island the
	 * world has built (and the ones near players that may be half built) plus the corridors their bridges run in,
	 * a budget of blocks per tick, then forgets them ({@link IslandsState#reset}), records the new seed and mix and
	 * starts the runtime again, which builds the new spawn island at once; players are put on it. Players fly while
	 * the old islands go. Everything in the cleared boxes goes, the player's own builds included.
	 *
	 * <p>Interrupted (the world is closed half way): the state still names the old field, so the next start sees
	 * the old islands as built, with holes; {@code islands regen} again finishes the job.
	 */
	private static final class Regen {
		/** Blocks read per tick (most of a box is air). */
		private static final int READS_PER_TICK = 80_000;
		/** Blocks removed per tick. */
		private static final int CLEARS_PER_TICK = 6_000;
		/** Islands within this many blocks of a player may be half built and are cleared too. */
		private static final int NEAR_PLAYERS = 170;
		/** Extra room round an island's box (outline and pieces) and round a bridge's line. */
		private static final int PAD = 4;
		private static final int CORRIDOR = 10;
		private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		private static final BlockState AIR = Blocks.AIR.defaultBlockState();

		private static volatile @Nullable Regen active;

		/** A box to clear; {@code loadedOnly}: skip columns in unloaded chunks (nothing was ever built there). */
		private record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean loadedOnly) {
			long volume() {
				return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
			}
		}

		private final Path root;
		private final Settings next;
		private final List<Box> boxes;
		private final long total;
		private long done;
		private int box;
		private int x;
		private int y;
		private int z;
		private boolean fresh = true;
		private int cleared;

		private Regen(Path root, Settings next, List<Box> boxes) {
			this.root = root;
			this.next = next;
			this.boxes = boxes;
			long t = 0;
			for (Box b : boxes) {
				t += b.volume();
			}
			this.total = Math.max(1, t);
		}

		static void begin(MinecraftServer server, Settings next) {
			if (active != null) {
				T3Craft.LOGGER.info("[islands] a regeneration is already running");
				return;
			}
			Path root = VanillaDemoWorld.worldRoot(server);
			IslandsRuntime.stop(); // saves what it built
			IslandsState st = IslandsState.load(root);
			IslandField old = new IslandField(st.seed(), IslandsWorld.BASE_Y, st.mix());
			Map<String, Island> islands = new LinkedHashMap<>();
			Set<String> built = st.islandIds();
			for (String id : built) {
				int[] cell = IslandField.cellOfId(id);
				if (cell != null) {
					islands.put(id, old.island(cell[0], cell[1]));
				}
			}
			Set<String> near = new HashSet<>();
			for (ServerPlayer p : players(server)) {
				for (Island i : old.within(p.getBlockX(), p.getBlockZ(), NEAR_PLAYERS)) {
					islands.putIfAbsent(i.id(), i);
					near.add(i.id());
				}
			}
			List<Box> boxes = new ArrayList<>();
			BridgeNetwork network = new BridgeNetwork(old);
			for (Island i : islands.values()) {
				for (Island n : old.neighbours(i)) {
					if (i.id().compareTo(n.id()) < 0 && islands.containsKey(n.id())) {
						boxes.add(corridor(i, n, !built.contains(i.id()) && !built.contains(n.id())));
					}
				}
				// Diagonal bridges (#32) run across the corner of four cells, outside every axis corridor: clear the box
				// of the ones built, or that may be half built because a player stood near both islands.
				for (int dz = -1; dz <= 1; dz += 2) {
					Island n = old.island(i.gx() + 1, i.gz() + dz);
					if (!islands.containsKey(n.id())) {
						continue;
					}
					boolean wasBuilt = st.hasBridge(BridgePlanner.idOf(i, n));
					if (wasBuilt || (near.contains(i.id()) && near.contains(n.id()))) {
						Bridge b = network.bridge(i, n);
						if (b != null) {
							boxes.add(boxOf(b, !wasBuilt));
						}
					}
				}
			}
			for (Island i : islands.values()) {
				dev.t3craft.mapgen.Area a = i.area();
				boxes.add(new Box(a.minX() - PAD, i.lowestY() - PAD, a.minZ() - PAD, a.maxX() + PAD, i.highestY(), a.maxZ() + PAD,
						!built.contains(i.id())));
			}
			Regen r = new Regen(root, next, boxes);
			active = r;
			r.hover(server);
			T3Craft.LOGGER.info("[islands] regeneration: clearing {} islands ({} built) in {} boxes, then seed {}, mix {}", islands.size(), built.size(),
					boxes.size(), next.seed(), next.mix().text());
		}

		/** The box a bridge between two neighbour islands can be in: their centres' rectangle, widened, round both heights. */
		private static Box corridor(Island a, Island b, boolean loadedOnly) {
			return new Box(Math.min(a.cx(), b.cx()) - CORRIDOR, Math.min(a.top(), b.top()) - CORRIDOR, Math.min(a.cz(), b.cz()) - CORRIDOR,
					Math.max(a.cx(), b.cx()) + CORRIDOR, Math.max(a.top(), b.top()) + CORRIDOR, Math.max(a.cz(), b.cz()) + CORRIDOR, loadedOnly);
		}

		/** The box round every block of a planned bridge (a diagonal one: its line is no corridor of two cell centres). */
		private static Box boxOf(Bridge b, boolean loadedOnly) {
			int minX = Integer.MAX_VALUE;
			int minY = Integer.MAX_VALUE;
			int minZ = Integer.MAX_VALUE;
			int maxX = Integer.MIN_VALUE;
			int maxY = Integer.MIN_VALUE;
			int maxZ = Integer.MIN_VALUE;
			for (Placement p : b.blocks()) {
				BlockPos q = p.pos();
				minX = Math.min(minX, q.getX());
				minY = Math.min(minY, q.getY());
				minZ = Math.min(minZ, q.getZ());
				maxX = Math.max(maxX, q.getX());
				maxY = Math.max(maxY, q.getY());
				maxZ = Math.max(maxZ, q.getZ());
			}
			return new Box(minX - PAD, minY - PAD, minZ - PAD, maxX + PAD, maxY + PAD, maxZ + PAD, loadedOnly);
		}

		static void cancel() {
			if (active != null) {
				T3Craft.LOGGER.warn("[islands] regeneration interrupted; /t3craft islands regen finishes it");
			}
			active = null;
		}

		static void tick(MinecraftServer server) {
			Regen r = active;
			if (r == null) {
				return;
			}
			r.hover(server);
			if (r.step(server.overworld())) {
				active = null;
				r.finish(server);
			}
		}

		static @Nullable String describe() {
			Regen r = active;
			if (r == null) {
				return null;
			}
			return "regenerating: " + (int) (100 * r.done / r.total) + "% cleared (" + r.cleared + " blocks), then seed " + r.next.seed() + ", mix "
					+ r.next.mix().describe();
		}

		private static List<ServerPlayer> players(MinecraftServer server) {
			List<ServerPlayer> out = new ArrayList<>();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (Level.OVERWORLD.equals(p.level().dimension())) {
					out.add(p);
				}
			}
			return out;
		}

		/** Keeps players who may fly in the air while the ground goes. */
		private void hover(MinecraftServer server) {
			for (ServerPlayer p : players(server)) {
				if (p.getAbilities().mayfly && !p.getAbilities().flying) {
					p.getAbilities().flying = true;
					p.onUpdateAbilities();
				}
			}
		}

		/** Clears within this tick's budgets; true when every box is done. */
		private boolean step(ServerLevel level) {
			int reads = READS_PER_TICK;
			int clears = CLEARS_PER_TICK;
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			while (box < boxes.size()) {
				Box b = boxes.get(box);
				if (fresh) {
					x = b.minX();
					z = b.minZ();
					y = b.maxY();
					fresh = false;
				}
				while (x <= b.maxX()) {
					pos.set(x, b.minY(), z);
					boolean skip = b.loadedOnly() && !level.isLoaded(pos);
					if (skip) {
						done += y - b.minY() + 1L;
						y = b.minY() - 1;
					}
					while (y >= b.minY()) {
						if (reads <= 0 || clears <= 0) {
							return false;
						}
						pos.set(x, y, z);
						reads--;
						done++;
						if (!level.getBlockState(pos).isAir()) {
							level.setBlock(pos, AIR, FLAGS);
							clears--;
							cleared++;
						}
						y--;
					}
					y = b.maxY();
					if (++z > b.maxZ()) {
						z = b.minZ();
						x++;
					}
				}
				box++;
				fresh = true;
			}
			return true;
		}

		private void finish(MinecraftServer server) {
			IslandsState st = IslandsState.load(root);
			st.reset(next.seed(), next.mix());
			st.saveIfDirty();
			IslandsWorld.use(next.seed(), next.mix());
			IslandsRuntime.start(server, root); // builds the new spawn island at once and moves the world spawn onto it
			BlockPos to = IslandsWorld.field().origin().surface().offset(0, 0, 4);
			ServerLevel level = server.overworld();
			for (ServerPlayer p : players(server)) {
				p.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
				p.setDeltaMovement(Vec3.ZERO);
				p.resetFallDistance();
				if (p.getAbilities().flying) {
					p.getAbilities().flying = false;
					p.onUpdateAbilities();
				}
				p.sendSystemMessage(text("New field: seed " + next.seed() + ", mix " + next.mix().describe() + " (" + cleared + " old blocks cleared)"));
			}
			T3Craft.LOGGER.info("[islands] regeneration done: {} blocks cleared, seed {}, mix {}", cleared, next.seed(), next.mix().text());
		}
	}
}
