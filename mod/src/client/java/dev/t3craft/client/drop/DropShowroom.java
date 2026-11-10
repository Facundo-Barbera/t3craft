package dev.t3craft.client.drop;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.client.T3CraftCommands;
import dev.t3craft.client.demo.DemoWorldInfo;
import dev.t3craft.drop.DropScene;
import dev.t3craft.hq.PlanApplier;
import dev.t3craft.hq.Placement;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * feat/ingame-preview-drop: the world side of the preview drop, for the T3Craft HQ world only (anywhere
 * else it does nothing). Everything here runs on the integrated server's thread, except {@link #offer}
 * and the read-only {@code status*} getters.
 *
 * <ul>
 *   <li><b>The showroom</b> ({@link DropScene}) is built {@link #OFFSET_X} blocks west of the HQ origin, at the
 *       island's ground level: far enough from any HQ footprint that the builders never touch it, and on the
 *       same height so the void guard does not mind. It is applied as a <b>diff</b> against the last scene
 *       (changed cells only), a few thousand blocks per server tick, and only while somebody is in or near
 *       it, or has just asked to go there: while you are in the HQ, a changed file only marks the showroom
 *       stale, and the next visit builds it.</li>
 *   <li><b>The HQ portal</b> is an arch the player places with {@code /t3craft drop portal}; walking through
 *       it goes to the showroom, and the showroom's own arch comes back to where the HQ portal was placed.
 *       It is remembered per world in {@code t3craft-drop.json}. The builders know nothing about it: place it
 *       outside the building (the island's edge) or place it again after a rebuild that overwrote it.</li>
 * </ul>
 * Nothing here touches the HQ plan, the fingerprints or the marker.
 */
final class DropShowroom {
	/** The showroom's origin is this far west of the HQ origin. */
	static final int OFFSET_X = -2000;
	/** Cells applied per server tick. */
	private static final int BATCH = 6000;
	/** Ticks after a teleport during which a player triggers no arch. */
	private static final int COOLDOWN_TICKS = 60;
	/** The showroom counts as "visited" this many blocks beyond its floor. */
	private static final int NEAR = 24;
	private static final String STATE_FILE = "t3craft-drop.json";

	private static final DropShowroom INSTANCE = new DropShowroom();

	static DropShowroom get() {
		return INSTANCE;
	}

	/** The showroom's world origin: its north-west floor corner. */
	private record World(Path root, BlockPos origin) {
	}

	/** The HQ arch: its walk-through box, and where people reappear coming back. */
	private record HqPortal(BlockPos centre, boolean alongX, BlockPos min, BlockPos max, BlockPos arrive, float yaw) {
	}

	private record Bounds(int width, int depth, int height) {
	}

	private record Spot(BlockPos pos, float yaw) {
	}

	/** A summary for {@code /t3craft drop}. */
	record Status(boolean inHq, boolean built, int queuedBatches, @Nullable BlockPos portal, @Nullable BlockPos showroom) {
	}

	private volatile @Nullable World world;
	/** The latest scene the folder gave (any thread). */
	private final AtomicReference<DropScene.@Nullable Scene> pending = new AtomicReference<>();

	// server thread only
	private DropScene.@Nullable Scene target;
	/** A scene whose changes could not be worked out: skipped until another one arrives. */
	private DropScene.@Nullable Scene failed;
	private final ArrayDeque<List<Placement>> queue = new ArrayDeque<>();
	private @Nullable HqPortal portal;
	private @Nullable Bounds bounds;
	private @Nullable Spot lastHq;
	private final Map<UUID, Integer> cooldown = new HashMap<>();
	private boolean wantApply;

	// read-only copies for the client thread
	private volatile boolean built;
	private volatile int queuedBatches;
	private volatile @Nullable BlockPos portalAt;

	private DropShowroom() {
	}

	// ------------------------------------------------------------------ lifecycle and input

	/** True while the integrated server runs the T3Craft HQ world. */
	boolean active() {
		return world != null;
	}

	/** A new scene from the folder (any thread). It is applied when somebody visits. */
	void offer(DropScene.Scene scene) {
		pending.set(scene);
	}

	Status status() {
		World w = world;
		return new Status(w != null, built, queuedBatches, portalAt, w == null ? null : w.origin());
	}

	/** Server thread, {@code SERVER_STARTED}. */
	void onServerStarted(MinecraftServer server) {
		reset();
		DemoWorldInfo info = DemoWorldInfo.of(server);
		if (info == null) {
			world = null;
			return;
		}
		World w = new World(info.root(), new BlockPos(info.originX() + OFFSET_X, info.originY() - 1, info.originZ()));
		readState(w);
		world = w;
		T3Craft.LOGGER.info("[drop] preview drop on in the HQ world: showroom at {}, portal {}", w.origin().toShortString(),
				portal == null ? "not placed" : portal.centre().toShortString());
	}

	/** Server thread, {@code SERVER_STOPPING}. */
	void onServerStopping() {
		world = null;
		reset();
	}

	private void reset() {
		target = null;
		failed = null;
		queue.clear();
		portal = null;
		bounds = null;
		lastHq = null;
		cooldown.clear();
		wantApply = false;
		built = false;
		queuedBatches = 0;
		portalAt = null;
	}

	// ------------------------------------------------------------------ the server tick

	/** Every server tick: keep the showroom current when somebody is there, drain the build queue, check the arches. */
	void tick(MinecraftServer server) {
		World w = world;
		if (w == null) {
			return;
		}
		if (wantApply || nearShowroom(server, w)) {
			wantApply = false;
			refresh(server, w);
		}
		if (!queue.isEmpty()) {
			applyBatch(server, w);
		}
		arches(server, w);
	}

	private boolean nearShowroom(MinecraftServer server, World w) {
		DropScene.Scene s = target != null ? target : pending.get();
		if (s == null) {
			return false;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (inShowroom(p.blockPosition(), w, s)) {
				return true;
			}
		}
		return false;
	}

	private static boolean inShowroom(BlockPos pos, World w, DropScene.Scene s) {
		BlockPos o = w.origin();
		return pos.getX() >= o.getX() - NEAR && pos.getX() < o.getX() + s.width() + NEAR && pos.getZ() >= o.getZ() - NEAR
				&& pos.getZ() < o.getZ() + s.depth() + NEAR;
	}

	private void arches(MinecraftServer server, World w) {
		HqPortal hp = portal;
		DropScene.Scene s = target;
		if (hp == null && s == null) {
			return;
		}
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
			if (hp != null && inBox(b, hp.min(), hp.max())) {
				go(server, id);
			} else if (s != null && inBox(b, w.origin().offset(s.triggerMin()), w.origin().offset(s.triggerMax()))) {
				back(server, id);
			}
		}
	}

	private static boolean inBox(BlockPos p, BlockPos min, BlockPos max) {
		return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY() && p.getZ() >= min.getZ()
				&& p.getZ() <= max.getZ();
	}

	// ------------------------------------------------------------------ building the showroom

	/** Queues what changed between the last scene and the latest one the folder gave. */
	private void refresh(MinecraftServer server, World w) {
		DropScene.Scene next = pending.get();
		if (next == null || next == target || next == failed) {
			return;
		}
		ServerLevel level = server.overworld();
		List<Placement> work;
		try {
			work = target == null ? firstWork(level, w, next) : DropScene.diff(target, next);
		} catch (RuntimeException e) {
			failed = next; // not again until the folder gives another scene, or a rebuild is asked for
			T3Craft.LOGGER.error("[drop] could not work out the showroom changes", e);
			return;
		}
		for (int i = 0; i < work.size(); i += BATCH) {
			queue.add(new ArrayList<>(work.subList(i, Math.min(work.size(), i + BATCH))));
		}
		target = next;
		built = true;
		queuedBatches = queue.size();
		Bounds old = bounds;
		Bounds grown = old == null ? new Bounds(next.width(), next.depth(), next.height())
				: new Bounds(Math.max(old.width(), next.width()), Math.max(old.depth(), next.depth()), Math.max(old.height(), next.height()));
		if (!grown.equals(old)) {
			bounds = grown;
			writeState(w);
		}
		T3Craft.LOGGER.info("[drop] showroom: {} exhibit(s), {} block(s) to change", next.slots().size(), work.size());
	}

	/**
	 * The first scene of a session: the world may hold the previous session's showroom, so what the scene
	 * lacks inside the recorded bounds is cleared, and only the cells that differ are placed.
	 */
	private List<Placement> firstWork(ServerLevel level, World w, DropScene.Scene next) {
		// The scene's own cells go first (the arrival area and the arch lead, so a visitor never lands on
		// nothing); the clearing goes last, over cells the scene does not use.
		List<Placement> out = new ArrayList<>();
		BlockPos o = w.origin();
		for (Placement p : next.cells().values()) {
			BlockState there = level.getBlockState(o.offset(p.rel()));
			if (p.be() == null && there.equals(p.state())) {
				continue;
			}
			out.add(p);
		}
		Bounds b = bounds;
		if (b != null) {
			BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
			for (int y = 0; y <= b.height() + 2; y++) {
				for (int z = 0; z < b.depth(); z++) {
					for (int x = 0; x < b.width(); x++) {
						m.set(o.getX() + x, o.getY() + y, o.getZ() + z);
						if (!level.getBlockState(m).isAir()) {
							BlockPos rel = new BlockPos(x, y, z);
							if (!next.cells().containsKey(rel)) {
								out.add(new Placement(rel, Blocks.AIR.defaultBlockState()));
							}
						}
					}
				}
			}
		}
		return out;
	}

	private void applyBatch(MinecraftServer server, World w) {
		List<Placement> batch = queue.poll();
		if (batch == null) {
			return;
		}
		try {
			PlanApplier.apply(server.overworld(), w.origin(), batch);
		} catch (RuntimeException e) {
			T3Craft.LOGGER.error("[drop] showroom batch failed", e);
		}
		queuedBatches = queue.size();
	}

	/** Forgets what was built and checks every block of the showroom against the world again. */
	void rebuild(MinecraftServer server, UUID id) {
		World w = world;
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (w == null || p == null) {
			return;
		}
		target = null;
		failed = null;
		queue.clear();
		wantApply = true;
		say(p, "rebuilding the showroom (only the blocks that differ are placed)");
	}

	// ------------------------------------------------------------------ going and coming back

	/** To the showroom (builds or updates it first). */
	void go(MinecraftServer server, UUID id) {
		World w = world;
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p == null) {
			return;
		}
		if (w == null) {
			say(p, "the showroom lives in the T3Craft HQ world (title screen > T3Craft HQ)");
			return;
		}
		pending.compareAndSet(null, DropScene.build(List.of()));
		DropScene.Scene s = pending.get();
		wantApply = false;
		refresh(server, w);
		applyBatch(server, w); // the first batch holds the arrival area and the arch: it is there when the player lands
		DropScene.Scene shown = target != null ? target : s;
		if (!inShowroom(p.blockPosition(), w, shown)) {
			lastHq = new Spot(p.blockPosition(), p.getYRot());
		}
		teleport(server, p, w.origin().offset(shown.arrival()), shown.arrivalYaw());
		int n = shown.slots().size();
		say(p, n == 0 ? "showroom: no schematics yet; drop .nbt / .schem files in the preview-drop folder (/t3craft drop)"
				: "showroom: " + n + " schematic(s); walk through the arch to come back (or /t3craft drop back)");
	}

	/** Back to the HQ: the portal's side if there is one, else where the last trip started, else the world spawn. */
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
		HqPortal hp = portal;
		Spot last = lastHq;
		if (hp != null) {
			teleport(server, p, hp.arrive(), hp.yaw());
		} else if (last != null) {
			teleport(server, p, last.pos(), last.yaw());
		} else {
			teleport(server, p, server.overworld().getRespawnData().pos(), p.getYRot());
		}
		say(p, "back in the HQ");
	}

	/** Places the HQ arch four blocks in front of the player (replacing the one placed before). */
	void placePortal(MinecraftServer server, UUID id) {
		World w = world;
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		if (p == null) {
			return;
		}
		if (w == null) {
			say(p, "the portal goes in the T3Craft HQ world (title screen > T3Craft HQ)");
			return;
		}
		DropScene.Scene known = target != null ? target : pending.get();
		if (known != null && inShowroom(p.blockPosition(), w, known)) {
			say(p, "you are in the showroom; go back to the HQ and stand where the portal should be");
			return;
		}
		ServerLevel level = server.overworld();
		HqPortal old = portal;
		if (old != null) {
			List<Placement> gone = new ArrayList<>();
			for (Placement c : DropScene.portal(old.alongX(), false).cells()) {
				gone.add(new Placement(c.rel(), Blocks.AIR.defaultBlockState()));
			}
			PlanApplier.apply(level, old.centre(), gone);
		}
		Direction look = p.getDirection();
		boolean alongX = look.getAxis() == Direction.Axis.Z; // looking north or south: the opening spans x
		BlockPos feet = p.blockPosition();
		BlockPos centre = new BlockPos(feet.getX() + look.getStepX() * 4, feet.getY() - 1, feet.getZ() + look.getStepZ() * 4);
		PlanApplier.apply(level, centre, DropScene.portal(alongX, false).cells());
		portal = makePortal(centre, alongX, feet, look.getOpposite().toYRot());
		portalAt = centre;
		writeState(w);
		say(p, "portal placed at " + centre.toShortString() + "; walk through it to reach the showroom"
				+ " (the HQ builders do not know about it: keep it outside the building, or place it again after a rebuild)");
	}

	private static HqPortal makePortal(BlockPos centre, boolean alongX, BlockPos arrive, float yaw) {
		DropScene.Portal pr = DropScene.portal(alongX, false);
		return new HqPortal(centre, alongX, pr.triggerMin().offset(centre), pr.triggerMax().offset(centre), arrive, yaw);
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
			if (o.has("portal")) {
				JsonObject p = o.getAsJsonObject("portal");
				BlockPos centre = new BlockPos(p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt());
				BlockPos arrive = new BlockPos(p.get("ax").getAsInt(), p.get("ay").getAsInt(), p.get("az").getAsInt());
				portal = makePortal(centre, p.get("alongX").getAsBoolean(), arrive, p.get("yaw").getAsFloat());
				portalAt = centre;
			}
			if (o.has("showroom")) {
				JsonObject s = o.getAsJsonObject("showroom");
				bounds = new Bounds(s.get("width").getAsInt(), s.get("depth").getAsInt(), s.get("height").getAsInt());
			}
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.warn("[drop] could not read {} ({}); starting without it", STATE_FILE, e.getClass().getSimpleName());
		}
	}

	private void writeState(World w) {
		JsonObject o = new JsonObject();
		HqPortal hp = portal;
		if (hp != null) {
			JsonObject p = new JsonObject();
			p.addProperty("x", hp.centre().getX());
			p.addProperty("y", hp.centre().getY());
			p.addProperty("z", hp.centre().getZ());
			p.addProperty("alongX", hp.alongX());
			p.addProperty("ax", hp.arrive().getX());
			p.addProperty("ay", hp.arrive().getY());
			p.addProperty("az", hp.arrive().getZ());
			p.addProperty("yaw", hp.yaw());
			o.add("portal", p);
		}
		Bounds b = bounds;
		if (b != null) {
			JsonObject s = new JsonObject();
			s.addProperty("width", b.width());
			s.addProperty("depth", b.depth());
			s.addProperty("height", b.height());
			o.add("showroom", s);
		}
		try {
			Files.writeString(w.root().resolve(STATE_FILE), o.toString(), StandardCharsets.UTF_8);
		} catch (IOException | RuntimeException e) {
			T3Craft.LOGGER.warn("[drop] could not write {} ({})", STATE_FILE, e.getClass().getSimpleName());
		}
	}
}
