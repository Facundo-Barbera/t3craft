package dev.t3craft.hq;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.t3craft.T3Craft;
import dev.t3craft.client.npc.NpcInfo;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * {@code /t3craft debug hq-preview ...}: generates the HQ plan and places it instantly in a
 * <b>superflat singleplayer</b> test world (refused anywhere else), so the design can be looked at
 * without the real world builder. Merges into the {@code /t3craft} literal registered elsewhere.
 * <ul>
 *   <li>{@code hq-preview}: live threads if any are shown, otherwise the demo (3 projects × 4 threads).</li>
 *   <li>{@code hq-preview demo <projects> <threads>}: synthetic input.</li>
 *   <li>{@code hq-preview live}: the threads currently shown as NPCs.</li>
 *   <li>{@code hq-preview clear}: removes the last preview and forgets it.</li>
 * </ul>
 * The first preview is anchored so that you stand on the spawn anchor; later previews in the same
 * world reuse that origin and the previous layout and only apply the diff, which shows how wings
 * and desks are annexed when threads appear and disappear.
 *
 * <p>State lives in this instance only (no statics) and is dropped on disconnect.
 */
public final class HqPreviewCommand {
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private record Session(ResourceKey<Level> dimension, BlockPos origin, HqLayout layout, BuildPlan plan) {
	}

	private final Supplier<List<NpcInfo>> live;
	private volatile @Nullable Session session;

	private HqPreviewCommand(Supplier<List<NpcInfo>> live) {
		this.live = live;
	}

	/** Integration point: call once from the client initializer with the NPCs currently shown. */
	public static void register(Supplier<List<NpcInfo>> live) {
		HqPreviewCommand cmd = new HqPreviewCommand(live);
		ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(literal("t3craft").then(literal("debug")
				.then(literal("hq-preview")
						.executes(c -> cmd.preview(c.getSource(), null))
						.then(literal("live").executes(c -> cmd.preview(c.getSource(), cmd.liveInput())))
						.then(literal("demo")
								.executes(c -> cmd.preview(c.getSource(), demo(3, 4)))
								.then(argument("projects", IntegerArgumentType.integer(0, 24))
										.then(argument("threads", IntegerArgumentType.integer(0, 64))
												.executes(c -> cmd.preview(c.getSource(), demo(IntegerArgumentType.getInteger(c, "projects"),
														IntegerArgumentType.getInteger(c, "threads")))))))
						.then(literal("clear").executes(c -> cmd.clear(c.getSource())))))));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> cmd.session = null);
	}

	private HqInput liveInput() {
		HqInput.Builder b = HqInput.builder();
		for (NpcInfo n : live.get()) {
			b.thread(n.groupId(), n.groupLabel(), n.envLabel(), n.id());
		}
		return b.build();
	}

	static HqInput demo(int projects, int threads) {
		String[] names = {"t3craft", "forja", "chopshop", "orchestrator", "telar", "ozom-trade", "iaware", "pendientes"};
		HqInput.Builder b = HqInput.builder();
		for (int p = 0; p < projects; p++) {
			String key = "demo:p" + p;
			String label = names[p % names.length] + (p >= names.length ? " " + (p / names.length + 1) : "");
			b.project(key, label, "demo");
			for (int t = 0; t < threads; t++) {
				b.thread(key, label, "demo", "demo:p" + p + "-t" + t);
			}
		}
		return b.build();
	}

	private int preview(FabricClientCommandSource src, @Nullable HqInput requested) {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) {
			src.sendError(prefix("hq-preview only works in a singleplayer (superflat) test world"));
			return 0;
		}
		HqInput input = requested;
		if (input == null) {
			HqInput l = liveInput();
			input = l.projects().isEmpty() ? demo(3, 4) : l;
		}
		ResourceKey<Level> dim = mc.player.level().dimension();
		BlockPos feet = mc.player.blockPosition();
		HqInput in = input;
		server.execute(() -> {
			ServerLevel level = server.getLevel(dim);
			if (level == null || !level.isFlat()) {
				reply(mc, src, prefix("refused: this is not a superflat world (hq-preview writes blocks; use a superflat test world)"), true);
				return;
			}
			Session prev = session;
			if (prev != null && !prev.dimension().equals(dim)) {
				prev = null;
			}
			HqGenerator.Result r = HqGenerator.plan(in, prev == null ? null : prev.layout());
			BlockPos origin;
			if (prev != null) {
				origin = prev.origin();
			} else {
				Anchor spawn = r.plan().anchor(AnchorNames.SPAWN);
				BlockPos sr = spawn == null ? BlockPos.ZERO : spawn.block();
				origin = feet.subtract(sr);
			}
			BlockPos o = origin;
			List<Placement> todo = BuildPlan.diff(prev == null ? null : prev.plan(), r.plan(), rel -> vacated(level, o, rel));
			try {
				PlanApplier.Stats st = PlanApplier.apply(level, origin, todo);
				session = new Session(dim, origin, r.layout(), r.plan());
				reply(mc, src, prefix(String.format(java.util.Locale.ROOT,
						"%d project(s), %d thread(s): %d of %d cells applied (%d changed, %d reshaped) in %d ms; footprint %dx%d, origin %s",
						in.projects().size(), in.threadCount(), todo.size(), r.plan().size(), st.changed(), st.shaped(), st.millis(),
						r.plan().footprint().sizeX(), r.plan().footprint().sizeZ(), origin.toShortString())), false);
			} catch (RuntimeException e) {
				T3Craft.LOGGER.error("hq-preview failed", e);
				reply(mc, src, prefix("hq-preview failed: " + e), true);
			}
		});
		return 1;
	}

	private int clear(FabricClientCommandSource src) {
		Minecraft mc = Minecraft.getInstance();
		IntegratedServer server = mc.getSingleplayerServer();
		Session s = session;
		if (server == null || s == null) {
			src.sendError(prefix("no preview to clear"));
			return 0;
		}
		server.execute(() -> {
			ServerLevel level = server.getLevel(s.dimension());
			if (level == null || !level.isFlat()) {
				reply(mc, src, prefix("refused: not a superflat world"), true);
				return;
			}
			List<Placement> todo = new java.util.ArrayList<>(s.plan().size());
			for (Placement p : s.plan().placements()) {
				todo.add(new Placement(p.rel(), vacated(level, s.origin(), p.rel())));
			}
			PlanApplier.Stats st = PlanApplier.apply(level, s.origin(), todo);
			session = null;
			reply(mc, src, prefix("cleared " + st.placed() + " cells"), false);
		});
		return 1;
	}

	/** What a cell the HQ no longer uses becomes: the superflat's grass at floor level, air above, as-is below. */
	private static BlockState vacated(ServerLevel level, BlockPos origin, BlockPos rel) {
		if (rel.getY() > 0) {
			return AIR;
		}
		if (rel.getY() == 0) {
			return Blocks.GRASS_BLOCK.defaultBlockState();
		}
		return level.getBlockState(origin.offset(rel));
	}

	private static void reply(Minecraft mc, FabricClientCommandSource src, Component msg, boolean error) {
		mc.execute(() -> {
			if (error) {
				src.sendError(msg);
			} else {
				src.sendFeedback(msg);
			}
		});
	}

	private static Component prefix(String msg) {
		return Component.literal("[T3Craft HQ] ").withStyle(ChatFormatting.AQUA).append(Component.literal(msg).withStyle(ChatFormatting.GRAY));
	}
}
