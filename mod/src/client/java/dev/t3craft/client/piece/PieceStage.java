package dev.t3craft.client.piece;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.demo.DemoWorldInfo;
import dev.t3craft.hq.PlanApplier;
import dev.t3craft.hq.Placement;
import dev.t3craft.piece.PieceModel;
import dev.t3craft.piece.PieceScene;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * feat/piece-catalog-preview: the world side of the piece catalog, for the T3Craft HQ world only. Everything here
 * runs on the integrated server's thread, except {@link #offer}, {@link #clear} and the read-only {@code status}.
 *
 * <p>The <b>stage</b> is one floor, far from the HQ and from the preview-drop showroom ({@link #OFFSET_X},
 * {@link #OFFSET_Z} from the HQ origin, at the island's ground level), sized to the piece on it
 * ({@link PieceScene}). One piece stands on it at a time. Changing piece, or reloading the same one, is a
 * <b>diff against what the world holds</b> in three steps, a few thousand blocks per server tick within a time
 * budget (a million-block building never stalls the game):
 * <ol>
 *   <li><b>place</b>: every cell of the new scene whose block differs from the world's;</li>
 *   <li><b>clear</b>: every cell of the previous scene that the new one does not have;</li>
 *   <li><b>sweep</b>, only when the previous scene is not known (the first piece of a session, or a switch while a
 *       change was still going up): the whole recorded area of the stage is checked and anything the new scene does
 *       not have is cleared.</li>
 * </ol>
 * Reloading a piece after its code changed only touches the blocks that changed. The area of the stage is
 * recorded in the world's save ({@value #STATE_FILE}) so even the leftovers of an earlier session are swept.
 *
 * <p>A return arch in the stage's lobby (and {@code /t3craft piece back}) takes you to where you came from.
 */
final class PieceStage {
	/** The stage's origin (north-west corner of its floor) is this far from the HQ origin. */
	static final int OFFSET_X = -2000;
	static final int OFFSET_Z = 3000;
	/** Server time per tick spent on the stage. */
	private static final long BUDGET_NS = 25_000_000L;
	/** Cells handed to the applier at a time. */
	private static final int BATCH = 1500;
	/** Cells inspected per round (most are equal to the world and cost only a read). */
	private static final int LOOK = 30_000;
	private static final int COOLDOWN_TICKS = 60;
	private static final String STATE_FILE = "t3craft-piece.json";
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private static final PieceStage INSTANCE = new PieceStage();

	static PieceStage get() {
		return INSTANCE;
	}

	private record World(Path root, BlockPos origin) {
	}

	/** The area of the stage ever used in this world. */
	private record Bounds(int width, int depth, int minY, int maxY) {
		Bounds union(PieceScene s) {
			return new Bounds(Math.max(width, s.width()), Math.max(depth, s.depth()), Math.min(minY, s.minY()), Math.max(maxY, s.maxY()));
		}

		static Bounds of(PieceScene s) {
			return new Bounds(s.width(), s.depth(), s.minY(), s.maxY());
		}
	}

	private record Spot(BlockPos pos, float yaw) {
	}

	/** A scene to show, from the worker. */
	private record Offer(@Nullable PieceScene scene, UUID player, boolean go) {
	}

	/** A summary for {@code /t3craft piece}. */
	record Status(boolean inHq, @Nullable String shown, boolean building, int percent, @Nullable BlockPos stage) {
	}

	private static final int PLACE = 0;
	private static final int CLEAR_PREV = 1;
	private static final int SWEEP = 2;
	private static final int DONE = 3;

	/** One change of the stage in progress. */
	private static final class Work {
		final PieceScene next;
		final @Nullable PieceScene prev;
		final @Nullable Bounds sweepArea;
		final UUID player;
		final long startedNs = System.nanoTime();
		int phase = PLACE;
		int i;
		int sx;
		int sy;
		int sz;
		int changed;

		Work(PieceScene next, @Nullable PieceScene prev, @Nullable Bounds sweepArea, UUID player) {
			this.next = next;
			this.prev = prev;
			this.sweepArea = sweepArea;
			this.player = player;
		}

		/** Moves to the next phase this work has. */
		void advance() {
			i = 0;
			if (phase == PLACE && prev != null) {
				phase = CLEAR_PREV;
			} else if (phase <= CLEAR_PREV && sweepArea != null) {
				phase = SWEEP;
				sx = 0;
				sz = 0;
				sy = sweepArea.maxY();
			} else {
				phase = DONE;
			}
		}

		int percent() {
			if (phase >= DONE) {
				return 100;
			}
			int n = Math.max(1, next.model().size());
			return phase == PLACE ? Math.min(99, (int) (100L * i / n)) : 99;
		}
	}

	private volatile @Nullable World world;
	private final ConcurrentLinkedQueue<Offer> offers = new ConcurrentLinkedQueue<>();

	// server thread only
	private @Nullable PieceScene current;
	private @Nullable Work work;
	private @Nullable Bounds bounds;
	private @Nullable Spot lastHq;
	private final Map<UUID, Integer> cooldown = new HashMap<>();

	// read-only copies for the client thread
	private volatile @Nullable String shownId;
	private volatile boolean building;
	private volatile int percent;

	private PieceStage() {
	}

	// ------------------------------------------------------------------ lifecycle and input

	/** True while the integrated server runs the T3Craft HQ world. */
	boolean active() {
		return world != null;
	}

	/** A scene to put on the stage (any thread). {@code go}: take the player there when it is ready to stand on. */
	void offer(PieceScene scene, UUID player, boolean go) {
		offers.add(new Offer(scene, player, go));
	}

	/** Takes everything off the stage (any thread). */
	void clear(UUID player) {
		offers.add(new Offer(null, player, false));
	}

	Status status() {
		World w = world;
		return new Status(w != null, shownId, building, percent, w == null ? null : w.origin());
	}

	/** Server thread, {@code SERVER_STARTED}. */
	void onServerStarted(MinecraftServer server) {
		reset();
		DemoWorldInfo info = DemoWorldInfo.of(server);
		if (info == null) {
			world = null;
			return;
		}
		World w = new World(info.root(), new BlockPos(info.originX() + OFFSET_X, info.originY() - 1, info.originZ() + OFFSET_Z));
		readState(w);
		world = w;
		T3Craft.LOGGER.info("[piece] piece stage on in the HQ world, at {}", w.origin().toShortString());
	}

	/** Server thread, {@code SERVER_STOPPING}. */
	void onServerStopping() {
		world = null;
		reset();
	}

	private void reset() {
		offers.clear();
		current = null;
		work = null;
		bounds = null;
		lastHq = null;
		cooldown.clear();
		shownId = null;
		building = false;
		percent = 0;
	}

	// ------------------------------------------------------------------ the server tick

	/** Every server tick: take what the worker handed over, keep building, watch the return arch. */
	void tick(MinecraftServer server) {
		World w = world;
		if (w == null) {
			return;
		}
		Offer o;
		while ((o = offers.poll()) != null) {
			take(server, w, o);
		}
		if (work != null) {
			step(server.overworld(), w);
		}
		if (current != null || work != null) {
			arch(server, w);
		}
	}

	private void take(MinecraftServer server, World w, Offer o) {
		ServerPlayer p = server.getPlayerList().getPlayer(o.player());
		PieceScene next = o.scene() == null ? PieceScene.blank() : o.scene();
		Work running = work;
		Bounds known = bounds;
		PieceScene prev = running != null ? null : current;
		// the previous scene is only exact when nothing was half-built: otherwise (and for the first scene of a
		// session, when the world may hold an older session's) the whole recorded area is checked
		Bounds sweep = prev != null ? null : known;
		if (!next.isBlank()) {
			Bounds grown = known == null ? Bounds.of(next) : known.union(next);
			if (!grown.equals(known)) {
				bounds = grown;
				writeState(w);
			}
		}
		work = new Work(next, prev, sweep, o.player());
		building = true;
		percent = 0;
		if (o.go() && p != null && !next.isBlank()) {
			step(server.overworld(), w); // the first cells are the lobby: it is there when the player lands
			BlockPos here = p.blockPosition();
			if (!inStage(here, w, next)) {
				lastHq = new Spot(here, p.getYRot());
			}
			teleport(server, p, w.origin().offset(next.arrival()), next.arrivalYaw());
		}
		T3Craft.LOGGER.info("[piece] stage: {} ({} cells, {} to clear, sweep {})", next.isBlank() ? "clear" : next.id(), next.model().size(),
				prev == null ? 0 : prev.model().size(), sweep != null);
	}

	/** Does up to {@link #BUDGET_NS} of the work in progress. */
	private void step(ServerLevel level, World w) {
		Work k = work;
		if (k == null) {
			return;
		}
		long deadline = System.nanoTime() + BUDGET_NS;
		List<Placement> batch = new ArrayList<>(BATCH);
		while (k.phase < DONE && System.nanoTime() < deadline) {
			batch.clear();
			collect(level, w, k, batch);
			if (!batch.isEmpty()) {
				try {
					PlanApplier.apply(level, w.origin(), batch);
					k.changed += batch.size();
				} catch (RuntimeException e) {
					T3Craft.LOGGER.error("[piece] a batch of the stage failed", e);
				}
			}
		}
		percent = k.percent();
		if (k.phase >= DONE) {
			finish(level, w, k);
		}
	}

	/** Fills {@code out} with what the work still has to change, looking at no more than {@link #LOOK} cells. */
	private void collect(ServerLevel level, World w, Work k, List<Placement> out) {
		BlockPos o = w.origin();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		PieceModel nextModel = k.next.model();
		int looked = 0;
		while (out.size() < BATCH && looked < LOOK && k.phase < DONE) {
			looked++;
			if (k.phase == PLACE) {
				if (k.i >= nextModel.size()) {
					k.advance();
					continue;
				}
				int idx = k.i++;
				long key = nextModel.key(idx);
				m.set(o.getX() + BlockPos.getX(key), o.getY() + BlockPos.getY(key), o.getZ() + BlockPos.getZ(key));
				CompoundTag tag = nextModel.tag(idx);
				if (tag == null && level.getBlockState(m).equals(nextModel.state(idx))) {
					continue;
				}
				out.add(nextModel.placement(idx));
			} else if (k.phase == CLEAR_PREV) {
				PieceScene prev = k.prev;
				if (prev == null || k.i >= prev.model().size()) {
					k.advance();
					continue;
				}
				int idx = k.i++;
				long key = prev.model().key(idx);
				if (nextModel.contains(key)) {
					continue;
				}
				m.set(o.getX() + BlockPos.getX(key), o.getY() + BlockPos.getY(key), o.getZ() + BlockPos.getZ(key));
				if (!level.getBlockState(m).isAir()) {
					out.add(new Placement(BlockPos.of(key), AIR));
				}
			} else {
				Bounds area = k.sweepArea;
				if (area == null || k.sy < area.minY()) {
					k.advance();
					continue;
				}
				int x = k.sx;
				int y = k.sy;
				int z = k.sz;
				if (++k.sx >= area.width()) {
					k.sx = 0;
					if (++k.sz >= area.depth()) {
						k.sz = 0;
						k.sy--;
					}
				}
				m.set(o.getX() + x, o.getY() + y, o.getZ() + z);
				if (!level.getBlockState(m).isAir() && !nextModel.contains(BlockPos.asLong(x, y, z))) {
					out.add(new Placement(new BlockPos(x, y, z), AIR));
				}
			}
		}
	}

	private void finish(ServerLevel level, World w, Work k) {
		work = null;
		building = false;
		percent = 100;
		boolean blank = k.next.isBlank();
		current = blank ? null : k.next;
		shownId = blank ? null : k.next.id();
		long ms = (System.nanoTime() - k.startedNs) / 1_000_000L;
		T3Craft.LOGGER.info("[piece] stage: {} done, {} block(s) changed in {} ms", blank ? "clear" : k.next.id(), k.changed, ms);
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(k.player);
		if (p != null) {
			say(p, blank ? "the stage is clear" : k.next.title() + " is up: " + k.changed + " block(s) changed in " + ms / 1000.0 + " s");
		}
	}

	// ------------------------------------------------------------------ the arch and the trips

	private void arch(MinecraftServer server, World w) {
		PieceScene shown = work != null ? work.next : current;
		if (shown == null || shown.isBlank()) {
			return;
		}
		BlockPos min = w.origin().offset(shown.triggerMin());
		BlockPos max = w.origin().offset(shown.triggerMax());
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!Level.OVERWORLD.equals(p.level().dimension()) || p.isSpectator()) {
				continue;
			}
			UUID id = p.getUUID();
			Integer cd = cooldown.get(id);
			if (cd != null) {
				if (cd <= 1) {
					cooldown.remove(id);
				} else {
					cooldown.put(id, cd - 1);
				}
				continue;
			}
			BlockPos b = p.blockPosition();
			if (b.getX() >= min.getX() && b.getX() <= max.getX() && b.getY() >= min.getY() && b.getY() <= max.getY() && b.getZ() >= min.getZ()
					&& b.getZ() <= max.getZ()) {
				back(server, id);
			}
		}
	}

	private boolean inStage(BlockPos pos, World w, PieceScene s) {
		BlockPos o = w.origin();
		return pos.getX() >= o.getX() - 24 && pos.getX() < o.getX() + s.width() + 24 && pos.getZ() >= o.getZ() - 24 && pos.getZ() < o.getZ() + s.depth() + 24;
	}

	/** To the stage's lobby (the piece on it, or the last one). */
	void go(MinecraftServer server, UUID id) {
		World w = world;
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p == null) {
			return;
		}
		PieceScene shown = work != null ? work.next : current;
		if (w == null || shown == null || shown.isBlank()) {
			say(p, "nothing on the stage yet: /t3craft piece show <id>");
			return;
		}
		BlockPos here = p.blockPosition();
		if (!inStage(here, w, shown)) {
			lastHq = new Spot(here, p.getYRot());
		}
		teleport(server, p, w.origin().offset(shown.arrival()), shown.arrivalYaw());
	}

	/** Back to where the trip started, else to the world spawn. */
	void back(MinecraftServer server, UUID id) {
		World w = world;
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p == null) {
			return;
		}
		if (w == null) {
			say(p, "this is not the T3Craft HQ world");
			return;
		}
		Spot last = lastHq;
		if (last != null) {
			teleport(server, p, last.pos(), last.yaw());
		} else {
			teleport(server, p, server.overworld().getRespawnData().pos(), p.getYRot());
		}
		say(p, "back in the HQ");
	}

	private void teleport(MinecraftServer server, ServerPlayer p, BlockPos feet, float yaw) {
		ServerLevel level = server.overworld();
		p.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), yaw, p.getXRot(), true);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		p.needsSync = true;
		cooldown.put(p.getUUID(), COOLDOWN_TICKS);
	}

	private static void say(ServerPlayer p, String text) {
		p.sendSystemMessage(T3CraftCommands.prefix().append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
	}

	// ------------------------------------------------------------------ the per-world file

	private void readState(World w) {
		Path f = w.root().resolve(STATE_FILE);
		if (!Files.isRegularFile(f)) {
			return;
		}
		try {
			JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
			bounds = new Bounds(o.get("width").getAsInt(), o.get("depth").getAsInt(), o.get("minY").getAsInt(), o.get("maxY").getAsInt());
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.warn("[piece] could not read {} ({}); starting without it", STATE_FILE, e.getClass().getSimpleName());
		}
	}

	private void writeState(World w) {
		Bounds b = bounds;
		if (b == null) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("width", b.width());
		o.addProperty("depth", b.depth());
		o.addProperty("minY", b.minY());
		o.addProperty("maxY", b.maxY());
		try {
			Files.writeString(w.root().resolve(STATE_FILE), o.toString(), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.warn("[piece] could not write {} ({})", STATE_FILE, e.getClass().getSimpleName());
		}
	}
}
