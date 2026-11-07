package dev.t3craft.client.npc.behavior;

import com.mojang.math.Transformation;
import dev.t3craft.client.npc.NpcInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Drives the thread NPCs: one {@link NpcBrain} per NPC, fed with the thread's {@link Mode}, the
 * spots from the {@link AnchorSource} (physical base) layered over the {@link GridLayout} fallback,
 * and the player's position; applies the brain's pose to the avatar and panel, and owns the extra
 * client-only entities it adds (speech bubble, floating attention icon). Client thread only.
 *
 * <p>Hot-swap / reload safety: no static state; everything lives in this instance and is dropped
 * by {@link #reset} (level change, {@code NpcRegistry.clear}). Entities are tracked by reference,
 * never looked up by id and cast.
 */
public final class BehaviorDirector {
	/** What the director gets per NPC from the registry each tick. */
	public record Body(String id, NpcInfo info, LivingEntity avatar, Display.@Nullable TextDisplay panel) {
	}

	static final double PANEL_Y = 2.45;
	static final double BUBBLE_Y = 3.05;
	static final double LINE_H = 0.27;
	static final double CROUCH_DROP = 0.3;

	private static final class Overlay {
		Display.@Nullable TextDisplay bubble;
		Display.@Nullable TextDisplay icon;
		@Nullable Component bubbleText;
		int bubbleLines;
		/** The panel text last measured and its visible line count (getString() only when the text changes). */
		@Nullable Component panelText;
		int panelLines;
		@Nullable String lastSummary;
		boolean seenSummary;
		boolean crouching;
	}

	private final Map<String, NpcBrain> brains = new HashMap<>();
	private final Map<String, Overlay> overlays = new HashMap<>();
	private AnchorSource anchors = AnchorSource.NONE;
	private SummarySource summaries = SummarySource.ACTIVITY;
	/** Client-only entity ids for bubbles and icons, far from the registry's range. */
	private int nextClientId = -3_000_000;
	private int tick;
	/** ATTENTION NPCs coming to the player (inventario item 16). */
	private final ApproachPlanner approaches = new ApproachPlanner();
	private @Nullable ApproachConfig approachOverride;
	private ApproachConfig.@Nullable Watcher approachConfig;
	/** Shared A* + collision over the current level (route cache and node budget live across ticks). */
	private @Nullable Navigation navigation;
	private @Nullable ClientLevel navigationLevel;
	/** Settled NPCs going home (item 22); null value = requested, starts next tick. Lazy: hot-swap safe. */
	private @Nullable Map<String, @Nullable Departure> departures;

	/** Plug in a physical base (null = none, grid layout only). See {@link AnchorSource}. */
	public void setAnchorSource(@Nullable AnchorSource source) {
		this.anchors = source == null ? AnchorSource.NONE : source;
	}

	/** Plug in the speech-bubble text (null = {@link SummarySource#ACTIVITY}). */
	public void setSummarySource(@Nullable SummarySource source) {
		this.summaries = source == null ? SummarySource.ACTIVITY : source;
	}

	/** Force the approach settings (null = read {@code npcApproach} from {@code config/t3craft.json}). */
	public void setApproachConfig(@Nullable ApproachConfig cfg) {
		this.approachOverride = cfg;
	}

	private ApproachConfig approachConfig() {
		if (approachOverride != null) {
			return approachOverride;
		}
		if (approachConfig == null) {
			approachConfig = new ApproachConfig.Watcher(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("t3craft.json"), 100);
		}
		return approachConfig.get();
	}

	public @Nullable NpcBrain brain(String npcId) {
		return brains.get(npcId);
	}

	/** Where the brain wants the NPC (logical position, ahead of the rendered one by a tick or two). */
	public @Nullable Vec3 position(String npcId) {
		NpcBrain b = brains.get(npcId);
		return b == null ? null : vec(b.pos());
	}

	/**
	 * The thread was settled (item 22): the NPC waves, walks to the exit and fades out, and stays
	 * hidden until T3 reflects the settle ({@code NpcInfo.settled()}). False if the NPC is unknown.
	 */
	public boolean depart(String npcId) {
		if (!brains.containsKey(npcId)) {
			return false;
		}
		departures().putIfAbsent(npcId, null);
		return true;
	}

	private Map<String, @Nullable Departure> departures() {
		return departures = dev.t3craft.debug.HotSwapFields.orInit(departures, HashMap::new);
	}

	public boolean departing(String npcId) {
		return departures().containsKey(npcId);
	}

	/** Forget one NPC (it was removed) and discard its overlay entities. */
	public void forget(String npcId, @Nullable ClientLevel lvl) {
		brains.remove(npcId);
		departures().remove(npcId);
		Overlay o = overlays.remove(npcId);
		if (o != null) {
			discard(lvl, o.bubble);
			discard(lvl, o.icon);
		}
	}

	/** Drop everything; discards our entities from {@code lvl} when given (null = level already gone). */
	public void reset(@Nullable ClientLevel lvl) {
		for (Overlay o : overlays.values()) {
			discard(lvl, o.bubble);
			discard(lvl, o.icon);
		}
		overlays.clear();
		brains.clear();
		departures().clear();
		approaches.reset();
		navigation = null;
		navigationLevel = null;
	}

	/**
	 * One client tick. {@code fallback} is the registry's provisional {@link GridLayout} (used for
	 * whatever the physical base leaves null); {@code bodies} are the spawned NPCs.
	 */
	public void tick(ClientLevel lvl, Player player, AnchorSource fallback, Collection<Body> bodies) {
		tick++;
		AnchorSource layout = new LayeredAnchors(anchors, fallback);
		if (navigation == null || navigationLevel != lvl) {
			navigation = Navigation.forLevel(new LevelBlocks(lvl));
			navigationLevel = lvl;
		}
		Navigation nav = navigation;
		nav.beginTick();
		Pos playerPos = pos(player.position());

		// Board slots: NPCs needing attention, most urgent first.
		Map<String, Integer> slotOf = new HashMap<>();
		List<Body> waiting = new ArrayList<>();
		for (Body b : bodies) {
			if (Mode.of(b.info().state()) == Mode.ATTENTION) {
				waiting.add(b);
			}
		}
		waiting.sort(Comparator.comparingInt((Body b) -> b.info().priority()).thenComparing(Body::id));
		for (int i = 0; i < waiting.size(); i++) {
			slotOf.put(waiting.get(i).id(), i);
		}
		Anchor board = layout.board();
		List<Anchor> explicitSlots = board == null ? List.of() : layout.boardSlots();

		List<ApproachPlanner.Candidate> candidates = new ArrayList<>(bodies.size());
		for (Body b : bodies) {
			NpcBrain known = brains.get(b.id());
			if (known != null) {
				candidates.add(new ApproachPlanner.Candidate(b.id(), known.pos(), Mode.of(b.info().state()) == Mode.ATTENTION, b.info().priority()));
			}
		}
		Map<String, Anchor> approachSpots = approaches.update(tick, approachConfig(), playerPos, candidates);

		Set<String> alive = new HashSet<>();
		for (Body b : bodies) {
			alive.add(b.id());
			NpcInfo info = b.info();
			NpcBrain brain = brains.computeIfAbsent(b.id(), id -> new NpcBrain(id, pos(b.avatar().position())));
			Mode mode = Mode.of(info.state());
			Anchor desk = layout.desk(b.id(), info.groupId());
			if (desk == null) {
				desk = new Anchor(brain.pos(), brain.bodyYaw());
			}
			Anchor home = Objects.requireNonNullElse(layout.home(info.groupId()), desk);
			Anchor rest = Objects.requireNonNullElse(layout.rest(b.id(), info.groupId()), desk);
			Integer slot = slotOf.get(b.id());
			Anchor boardSlot = board != null && slot != null ? BoardSlots.slot(board, explicitSlots, slot) : null;
			NpcBrain.Targets t = new NpcBrain.Targets(desk, boardSlot, home, layout.wanderRadius(info.groupId()), rest);

			Overlay o = overlays.computeIfAbsent(b.id(), id -> new Overlay());
			if (departures().containsKey(b.id()) && depart(lvl, b, brain, o, layout, nav, playerPos)) {
				continue;
			}
			String summary = summaries.summary(b.id(), info);
			if (o.seenSummary && !Objects.equals(summary, o.lastSummary)) {
				brain.summaryChanged();
			}
			o.seenSummary = true;
			o.lastSummary = summary;

			brain.approach(approachSpots.get(b.id()));
			brain.tick(mode, "error".equals(info.state()), t, playerPos, nav);
			apply(lvl, b, brain, o, summary, playerPos);
		}
		for (String id : new ArrayList<>(brains.keySet())) {
			if (!alive.contains(id)) {
				forget(id, lvl);
			}
		}
	}

	/** Drives a departing NPC; false once it is over (the brain takes over again). */
	private boolean depart(ClientLevel lvl, Body b, NpcBrain brain, Overlay o, AnchorSource layout, NpcBrain.Navigator nav, Pos player) {
		Departure d = departures().get(b.id());
		if (d == null) {
			Anchor exit = layout.exit();
			if (exit == null) {
				exit = layout.home(b.info().groupId());
			}
			d = new Departure(brain.pos(), brain.bodyYaw(), exit != null ? exit : new Anchor(brain.pos(), brain.bodyYaw()));
			departures().put(b.id(), d);
		}
		if (d.stage() == Departure.Stage.GONE && (b.info().settled() || d.expired())) {
			// Back (settle reflected: the HQ gave it a new spot; or T3 never confirmed). Hidden, so
			// the fresh brain placing it straight on its spot is not a visible teleport.
			departures().remove(b.id());
			brains.put(b.id(), new NpcBrain(b.id(), d.pos()));
			b.avatar().setInvisible(false);
			return false;
		}
		d.tick(nav, player);
		LivingEntity a = b.avatar();
		Pos p = d.pos();
		a.moveOrInterpolateTo(new Vec3(p.x(), p.y(), p.z()), d.yaw(), 0f);
		a.setYBodyRot(d.yaw());
		a.setYHeadRot(d.yaw());
		if (o.crouching) {
			o.crouching = false;
			a.setPose(Pose.STANDING);
		}
		if (d.stage() == Departure.Stage.WAVE && d.stageTicks() % 7 == 1) {
			a.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
		}
		a.setInvisible(!d.visible());
		discard(lvl, o.bubble);
		discard(lvl, o.icon);
		o.bubble = null;
		o.icon = null;
		o.bubbleText = null;
		Vec3 at = a.position();
		if (b.panel() != null) {
			// Hidden: park the panel under the avatar, out of sight (client-only entity).
			b.panel().moveOrInterpolateTo(new Vec3(at.x, at.y + (d.visible() ? PANEL_Y : -64), at.z), 0f, 0f);
		}
		if (d.stage() == Departure.Stage.FADE) {
			var r = lvl.getRandom();
			int n = d.stageTicks() == 1 ? 12 : 2;
			for (int i = 0; i < n; i++) {
				lvl.addParticle(i % 2 == 0 ? ParticleTypes.POOF : ParticleTypes.PORTAL, at.x + (r.nextDouble() - 0.5) * 0.8,
						at.y + r.nextDouble() * 1.8, at.z + (r.nextDouble() - 0.5) * 0.8, 0, 0.03, 0);
			}
		}
		return true;
	}

	private void apply(ClientLevel lvl, Body b, NpcBrain brain, Overlay o, @Nullable String summary, Pos player) {
		LivingEntity a = b.avatar();
		Pos p = brain.pos();
		Vec3 target = new Vec3(p.x(), p.y() + brain.hop(), p.z());
		if (brain.teleported() || a.position().distanceToSqr(target) > 64) {
			a.snapTo(target.x, target.y, target.z, brain.bodyYaw(), brain.pitch());
		} else {
			a.moveOrInterpolateTo(target, brain.bodyYaw(), brain.pitch());
		}
		a.setYBodyRot(brain.bodyYaw());
		a.setYHeadRot(brain.headYaw());
		if (brain.crouch() != o.crouching) {
			o.crouching = brain.crouch();
			a.setPose(o.crouching ? Pose.CROUCHING : Pose.STANDING);
		}
		switch (brain.swing()) {
			case MAIN -> a.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
			case OFF -> a.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, false);
			case NONE -> {
			}
		}

		// Overlays follow the rendered avatar (its interpolated position), so they stay in sync.
		Vec3 at = a.position();
		double drop = o.crouching ? CROUCH_DROP : 0;
		if (b.panel() != null) {
			b.panel().moveOrInterpolateTo(new Vec3(at.x, at.y + PANEL_Y - drop, at.z), 0f, 0f);
		}

		double dist = p.horizDist(player);
		// Stack above the panel's visible lines (fix/npc-labels) so bubble and panel never cross.
		int panelLines = 0;
		if (b.panel() != null) {
			Component pt = b.panel().getText();
			if (pt != o.panelText) {
				o.panelText = pt;
				o.panelLines = dev.t3craft.client.npc.NpcLabels.lines(pt.getString());
			}
			panelLines = o.panelLines;
		}
		double top = at.y + dev.t3craft.client.npc.NpcLabels.bubbleBase(PANEL_Y, BUBBLE_Y, LINE_H, panelLines) - drop;
		if (brain.bubbleWanted(dist)) {
			if (o.bubble == null || o.bubble.isRemoved()) {
				o.bubble = textDisplay(lvl, 0xB0101010, 1f);
				o.bubbleText = null;
			}
			List<String> lines = BubbleText.lines(summary, brain.mode() == null ? Mode.IDLE : brain.mode(), brain.error(), brain.emote());
			Component text = bubble(lines);
			if (!text.equals(o.bubbleText)) {
				o.bubble.setText(text);
				dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SET_TEXT);
				o.bubbleText = text;
				o.bubbleLines = lines.size();
			}
			o.bubble.moveOrInterpolateTo(new Vec3(at.x, top, at.z), 0f, 0f);
			top += o.bubbleLines * LINE_H + 0.1;
		} else if (o.bubble != null) {
			discard(lvl, o.bubble);
			o.bubble = null;
			o.bubbleText = null;
		}

		if (brain.iconWanted()) {
			if (o.icon == null || o.icon.isRemoved()) {
				o.icon = textDisplay(lvl, 0, 2.2f);
			}
			Component glyph = Component.literal(brain.error() ? "✖" : "!").withStyle(ChatFormatting.BOLD).withColor(brain.error() ? 0xFF5555 : 0xFFAA00);
			if (!glyph.equals(o.icon.getText())) {
				o.icon.setText(glyph);
				dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SET_TEXT);
			}
			double bob = Math.sin((tick + Math.abs(b.id().hashCode() % 40)) * 0.12) * 0.12;
			o.icon.moveOrInterpolateTo(new Vec3(at.x, top + 0.1 + bob, at.z), 0f, 0f);
		} else if (o.icon != null) {
			discard(lvl, o.icon);
			o.icon = null;
		}

		particles(lvl, brain, at);
	}

	private void particles(ClientLevel lvl, NpcBrain brain, Vec3 at) {
		var r = lvl.getRandom();
		switch (brain.emote()) {
			case CELEBRATE -> {
				if (tick % 4 == 0) {
					lvl.addParticle(ParticleTypes.HAPPY_VILLAGER, at.x + (r.nextDouble() - 0.5), at.y + 1.0 + r.nextDouble(), at.z + (r.nextDouble() - 0.5), 0, 0.05, 0);
				}
				if (tick % 20 == 0) {
					lvl.addParticle(ParticleTypes.FIREWORK, at.x, at.y + 2.2, at.z, (r.nextDouble() - 0.5) * 0.1, 0.1, (r.nextDouble() - 0.5) * 0.1);
				}
			}
			case MUSIC -> {
				if (tick % 25 == 0) {
					lvl.addParticle(ParticleTypes.NOTE, at.x, at.y + 2.1, at.z, r.nextDouble(), 0, 0);
				}
			}
			default -> {
			}
		}
	}

	static Component bubble(List<String> lines) {
		MutableComponent c = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			c.append(Component.literal((i == 0 ? "" : "\n") + lines.get(i)).withColor(i == 0 ? 0xFFFFFF : 0xE0E0E0));
		}
		return c;
	}

	private Display.TextDisplay textDisplay(ClientLevel lvl, int background, float scale) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, lvl);
		d.setId(nextClientId--);
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setBackgroundColor(background);
		d.setLineWidth(200);
		if (scale != 1f) {
			d.setTransformation(new Transformation(null, null, new Vector3f(scale, scale, scale), null));
		}
		lvl.addEntity(d);
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SPAWNED);
		return d;
	}

	private static void discard(@Nullable ClientLevel lvl, @Nullable Entity e) {
		if (e != null && lvl != null && !e.isRemoved()) {
			lvl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	/** Ground probe over the client level for the {@link GridLayout} (same walkability as the pathfinder). */
	public static GridLayout.Ground ground(ClientLevel lvl, double baseY) {
		return GridLayout.Ground.near(new Collision(new LevelBlocks(lvl)), baseY);
	}

	static Pos pos(Vec3 v) {
		return new Pos(v.x, v.y, v.z);
	}

	static Vec3 vec(Pos p) {
		return new Vec3(p.x(), p.y(), p.z());
	}
}
