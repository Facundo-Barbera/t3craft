package dev.t3craft.client.demo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.t3craft.T3Craft;
import dev.t3craft.client.config.ConfigManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * feat/ingame-preview-drop: read-only view of the official HQ world for other client modules (the preview
 * drop showroom), without touching {@link DemoClient}. Answers one question, "is this integrated server
 * running the T3Craft HQ save, and where is its origin?", with the same rules the demo uses (the configured
 * folder, a void or flat world as the save is, the {@code demo.origin} config).
 *
 * @param root the save folder
 */
public record DemoWorldInfo(Path root, int originX, int originY, int originZ, boolean voidWorld) {
	/** The HQ world {@code server} runs, or null when it runs anything else (or is not a singleplayer server). Any thread. */
	public static @Nullable DemoWorldInfo of(MinecraftServer server) {
		DemoConfig loaded = loadConfig();
		if (!VanillaDemoWorld.isDemoServer(server, loaded.spec().folderId())) {
			return null;
		}
		boolean voidWorld = VanillaDemoWorld.isVoid(server);
		DemoWorldSpec spec = loaded.forWorld(voidWorld).spec();
		return new DemoWorldInfo(VanillaDemoWorld.worldRoot(server), spec.originX(), spec.originY(), spec.originZ(), voidWorld);
	}

	/**
	 * fix/hq-load-build-portal: where the preview-drop portal stands, fixed by the HQ itself. World coordinates.
	 *
	 * @param centre  the arch's centre cell on the floor layer (its posts start one block above)
	 * @param alongX  the opening spans x (people walk through along z), in the world
	 * @param arrive  where people reappear coming back: on the hub's side of the arch (feet)
	 * @param yaw     the way they look on arrival (towards the hub)
	 */
	public record PortalSpot(BlockPos centre, boolean alongX, BlockPos arrive, float yaw) {
	}

	/**
	 * The portal's place in the main HQ, or null when it cannot be worked out yet (the empty HQ plan is still being
	 * computed in the background, or the layout has no spawn anchor). It never waits: ask again later. Any thread.
	 *
	 * <p>The spot comes from the plan, not from the player: in the citadel it is the <b>north end of the inner street,
	 * at the foot of the north ramp</b>, the mirror image of the spawn anchor (south end of that street): the arch
	 * stands on the street's axis, across the way down from the hub (opening towards the hub), 5 wide so it fits the
	 * street at any scale. In a layout without its own ground plan it stands 7 blocks in front of the spawn.
	 */
	public static @Nullable PortalSpot portalSpot(MinecraftServer server, DemoWorldInfo info) {
		try {
			String built = DemoClient.builtLayoutId(info.root());
			String layoutId = built != null ? built : dev.t3craft.client.hq.layout.HqLayoutConfig.selectedId();
			dev.t3craft.hq.HqStyle style = dev.t3craft.hq.HqStyles.forLayout(layoutId);
			java.util.concurrent.CompletableFuture<dev.t3craft.hq.BuildPlan> f = dev.t3craft.hq.HqEmptyPlan.start(style);
			if (!f.isDone() || f.isCompletedExceptionally()) {
				return null;
			}
			dev.t3craft.hq.BuildPlan empty = f.join();
			dev.t3craft.hq.Anchor spawn = empty.anchor(dev.t3craft.hq.AnchorNames.SPAWN);
			if (spawn == null) {
				return null;
			}
			int lift = DemoHq.lift(info.originY(), server.overworld().getMinY(), empty.footprint().minY());
			dev.t3craft.hq.HqSite site = DemoHq.worldSite(new BlockPos(info.originX(), info.originY(), info.originZ()), lift);
			BlockPos s = spawn.block();
			BlockPos centreRel;
			int towardsHub; // +1: the hub is at +z of the arch (plan frame), -1: at -z
			if (style.ownLayout()) {
				centreRel = new BlockPos(-s.getX(), s.getY() - 1, -s.getZ());
				towardsHub = centreRel.getZ() < 0 ? 1 : -1;
			} else {
				centreRel = new BlockPos(s.getX(), s.getY() - 1, s.getZ() + 7);
				towardsHub = -1;
			}
			BlockPos centre = site.cell(centreRel);
			BlockPos arrive = site.cell(centreRel.offset(0, 1, 3 * towardsHub));
			net.minecraft.core.Direction hubSide = towardsHub > 0 ? net.minecraft.core.Direction.SOUTH : net.minecraft.core.Direction.NORTH;
			boolean alongX = site.direction(net.minecraft.core.Direction.EAST).getAxis() == net.minecraft.core.Direction.Axis.X;
			return new PortalSpot(centre, alongX, arrive, site.direction(hubSide).toYRot());
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static DemoConfig loadConfig() {
		try {
			Path f = ConfigManager.get().file();
			if (Files.exists(f)) {
				JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
				return DemoConfig.fromRoot(e.isJsonObject() ? e.getAsJsonObject() : null);
			}
		} catch (Exception e) {
			T3Craft.LOGGER.warn("[drop] could not read config, using defaults ({})", e.getClass().getSimpleName());
		}
		return DemoConfig.defaults();
	}
}
