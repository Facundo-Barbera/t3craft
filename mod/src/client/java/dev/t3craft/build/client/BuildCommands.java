package dev.t3craft.build.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.t3craft.build.BuildHandle;
import dev.t3craft.build.BuildJob;
import dev.t3craft.build.BuildPlan;
import dev.t3craft.build.DemoPlans;
import dev.t3craft.survival.BuildMaterials;
import dev.t3craft.survival.PlayContexts;
import dev.t3craft.survival.Survival;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Rotation;

/**
 * Client-side debug commands, merged by Brigadier under the existing {@code /t3craft} literal
 * (registered separately, so {@code T3CraftCommands} is untouched):
 * <ul>
 *   <li>{@code /t3craft debug build-demo [blocksPerTick] [crew]}: a crew builds a test shed in
 *   front of you (default 0.5 blocks/tick, 3 builders).</li>
 *   <li>{@code /t3craft debug build-pause | build-resume | build-cancel}: all active builds.</li>
 *   <li>{@code /t3craft debug build-status}: phase and progress of each build.</li>
 * </ul>
 */
final class BuildCommands {
	static final double DEFAULT_BLOCKS_PER_TICK = 0.5;
	static final int DEFAULT_CREW = 3;

	private BuildCommands() {
	}

	static void register(BuildersClient builders) {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> build(dispatcher, builders));
	}

	private static void build(CommandDispatcher<FabricClientCommandSource> d, BuildersClient b) {
		d.register(literal("t3craft").then(literal("debug")
				.then(literal("build-demo")
						.executes(c -> demo(c, b, DEFAULT_BLOCKS_PER_TICK, DEFAULT_CREW))
						.then(argument("blocksPerTick", DoubleArgumentType.doubleArg(0.05, 64))
								.executes(c -> demo(c, b, DoubleArgumentType.getDouble(c, "blocksPerTick"), DEFAULT_CREW))
								.then(argument("crew", IntegerArgumentType.integer(1, 8))
										.executes(c -> demo(c, b, DoubleArgumentType.getDouble(c, "blocksPerTick"), IntegerArgumentType.getInteger(c, "crew"))))))
				.then(literal("build-pause").executes(c -> each(c, b, "paused", BuildHandle::pause)))
				.then(literal("build-resume").executes(c -> each(c, b, "resumed", BuildHandle::resume)))
				.then(literal("build-cancel").executes(c -> each(c, b, "cancelled", BuildHandle::cancel)))
				.then(literal("build-status").executes(c -> status(c, b)))));
	}

	private static int demo(CommandContext<FabricClientCommandSource> c, BuildersClient b, double blocksPerTick, int crew) {
		FabricClientCommandSource src = c.getSource();
		if (!b.available()) {
			src.sendError(prefix().append("build-demo needs a singleplayer world (integrated server)"));
			return 0;
		}
		LocalPlayer player = src.getPlayer();
		Direction facing = player.getDirection();
		BlockPos origin = player.blockPosition().relative(facing, 3);
		Rotation rotation = DemoPlans.rotationFacingViewer(facing);
		BuildPlan plan = DemoPlans.shed();
		try {
			BuildHandle h = b.build("shed", player.level().dimension(), origin, rotation, plan, blocksPerTick, crew);
			src.sendFeedback(prefix().append(Component.literal(String.format(Locale.ROOT,
					"%s: %d blocks at %s, %.2f blocks/tick, %d builders on their way", h.label(), plan.size(), origin.toShortString(), blocksPerTick, crew))));
			if (BuildMaterials.decide(Survival.rules(), PlayContexts.of(player, true)).charged()) {
				src.sendFeedback(prefix().append(Component.translatable("t3craft.survival.build_cost", BuildMaterials.describe(BuildMaterials.bill(plan).items()))));
			}
			return 1;
		} catch (IllegalStateException e) {
			src.sendError(prefix().append(e.getMessage()));
			return 0;
		}
	}

	private static int each(CommandContext<FabricClientCommandSource> c, BuildersClient b, String verb, Consumer<BuildHandle> action) {
		List<BuildHandle> all = b.active();
		all.forEach(action);
		c.getSource().sendFeedback(prefix().append(all.size() + " build(s) " + verb));
		return all.size();
	}

	private static int status(CommandContext<FabricClientCommandSource> c, BuildersClient b) {
		FabricClientCommandSource src = c.getSource();
		List<BuildHandle> all = b.active();
		src.sendFeedback(prefix().append(all.isEmpty() ? "no active builds" : all.size() + " active build(s)"));
		for (BuildHandle h : all) {
			BuildJob j = h.job();
			String detail = j == null
					? h.phase().name().toLowerCase(Locale.ROOT)
					: String.format(Locale.ROOT, "%s, job #%d %s %d/%d (%.0f%%), placed %d, skipped %d, failed %d",
							h.phase().name().toLowerCase(Locale.ROOT), j.id(), j.state().name().toLowerCase(Locale.ROOT),
							j.done(), j.total(), j.progress() * 100, j.placed(), j.skipped(), j.failed());
			MutableComponent line = Component.literal("  " + h.label() + ": " + detail);
			if (j != null && j.charged()) {
				line.append(String.format(Locale.ROOT, ", materials %d/%d", total(j.spentMaterials()), total(j.requiredMaterials())));
				if (j.waitingForMaterials()) {
					line.append(", ").append(Component.translatable("t3craft.survival.missing_materials", BuildMaterials.describe(j.missingMaterials())));
				}
			}
			src.sendFeedback(line.withStyle(ChatFormatting.GRAY));
		}
		return all.size();
	}

	private static int total(java.util.Map<String, Integer> items) {
		return items.values().stream().mapToInt(Integer::intValue).sum();
	}

	private static MutableComponent prefix() {
		return Component.literal("[T3Craft] ").withStyle(ChatFormatting.AQUA);
	}
}
