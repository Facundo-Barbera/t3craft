// Adapted from AgentCraft (https://github.com/blendi-remade/agentcraft)
// Copyright (c) 2026 AgentCraft contributors. MIT License.
package dev.t3craft.client.blocks;

import com.mojang.math.Transformation;
import dev.t3craft.blocks.LampStatus;
import dev.t3craft.blocks.StatusLampBlock;
import dev.t3craft.blocks.T3Blocks;
import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.hq.HqPresence;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.workstation.WorkstationThread;
import dev.t3craft.hq.Anchor;
import dev.t3craft.hq.AnchorNames;
import dev.t3craft.hq.BuildPlan;
import dev.t3craft.hq.construct.HqSiteFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The hub beacon without blocks: when no beacon lamp sits in the lantern cap (blocks module off, or an
 * HQ built before it), a client-only, full-bright {@link Display.TextDisplay} glows above the
 * lantern in the HQ's aggregate colour ({@link LampSignals#beacon}), breathing like the lamps
 * ({@link LampGlow}); motes rise from it while something needs you. Nothing registered, nothing sent
 * to a server. With the lamp in place ({@link LampGlow.BeaconMode#BLOCK}) the lamp's renderer draws
 * the beacon and this stays empty.
 *
 * <p>Height: centred {@value #LIFT} blocks above the cap block (just over the lightning rod), on the
 * hub's axis. Projections over the hub (e.g. a hologram) should stay clear of that column.
 *
 * <p>Entity ids count down from {@link #ID_BASE}; a removed entity (NpcRegistry's orphan sweep) is
 * re-created on the next tick.
 */
public final class HubBeacon {
	static final int ID_BASE = -7_500_000;
	/** Centre of the glow above the cap block's origin (the rod reaches y + 2). */
	static final float LIFT = 2.6f;
	private static final float SCALE = 3f;
	private static final Random RANDOM = new Random();

	private Display.@Nullable TextDisplay display;
	private int nextId = ID_BASE;
	private int lastBackground;
	private LampGlow.BeaconMode mode = LampGlow.BeaconMode.NONE;

	/** The beacon cell in plan coordinates: the {@code beacon} anchor, else 8 above the hub floor. */
	static @Nullable BlockPos spot(BuildPlan plan) {
		Anchor b = plan.anchor(AnchorNames.BEACON);
		if (b != null) {
			return b.block();
		}
		Anchor hub = plan.anchor(AnchorNames.HUB);
		return hub == null ? null : hub.block().above(8);
	}

	/** Whether the site module holding {@code rel} stands ({@code built} null = all, the demo). */
	static boolean standing(BlockPos rel, @Nullable Set<String> built) {
		if (built == null) {
			return true;
		}
		String owner = HqSiteFrame.owner(rel.getX(), rel.getZ());
		return owner != null && built.contains(owner);
	}

	LampGlow.BeaconMode mode() {
		return mode;
	}

	void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		HqPresence.Standing st = HqPresence.current();
		BlockPos rel = st == null ? null : spot(st.plan());
		BlockPos cell = rel == null || level == null || !standing(rel, st.built()) ? null : st.site().cell(rel);
		boolean lamp = cell != null && T3Blocks.active() && level.isLoaded(cell)
				&& level.getBlockState(cell).getBlock() instanceof StatusLampBlock;
		mode = LampGlow.beaconMode(cell != null, lamp);
		if (mode != LampGlow.BeaconMode.CLIENT) {
			clear(level);
			return;
		}
		LampStatus status = LampSignals.beacon(threads());
		double seconds = (level.getGameTime() % 1_000_000L) / 20.0;
		int bg = LampGlow.argb(status.color, LampGlow.alpha(LampGlow.level(status, seconds), 70, 230));
		double x = cell.getX() + 0.5;
		double y = cell.getY() + LIFT;
		double z = cell.getZ() + 0.5;
		Display.TextDisplay d = display;
		if (d == null || d.isRemoved() || d.level() != level || d.getX() != x || d.getY() != y || d.getZ() != z) {
			clear(level);
			d = spawn(level, x, y, z);
		}
		if (bg != lastBackground) {
			d.setBackgroundColor(bg);
			lastBackground = bg;
		}
		if (status == LampStatus.ATTENTION && !mc.isPaused() && RANDOM.nextFloat() < 0.35f) {
			level.addParticle(new DustParticleOptions(status.color, 0.9f), x + (RANDOM.nextDouble() - 0.5) * 1.2,
					y - 0.4 + RANDOM.nextDouble() * 0.8, z + (RANDOM.nextDouble() - 0.5) * 1.2, 0, 0.02, 0);
		}
	}

	private Display.TextDisplay spawn(ClientLevel level, double x, double y, double z) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		d.setId(nextId--);
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.setText(Component.literal("   "));
		d.setViewRange(4f);
		d.setTransformation(new Transformation(null, null, new Vector3f(SCALE), null));
		d.snapTo(x, y, z, 0f, 0f);
		level.addEntity(d);
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SPAWNED);
		display = d;
		lastBackground = 0;
		return d;
	}

	/** Removes the glow (null level = already gone). */
	void clear(@Nullable ClientLevel level) {
		Display.TextDisplay d = display;
		display = null;
		if (d != null && !d.isRemoved()) {
			ClientLevel l = level != null && d.level() == level ? level : d.level() instanceof ClientLevel cl ? cl : null;
			if (l != null) {
				l.removeEntity(d.getId(), Entity.RemovalReason.DISCARDED);
			}
		}
	}

	private static List<WorkstationThread> threads() {
		T3CraftClient c = T3CraftClient.get();
		List<NpcInfo> npcs = c == null ? List.of() : c.npcs().all();
		List<WorkstationThread> out = new ArrayList<>(npcs.size());
		for (NpcInfo n : npcs) {
			out.add(WorkstationThread.fromNpc(n));
		}
		return out;
	}
}
