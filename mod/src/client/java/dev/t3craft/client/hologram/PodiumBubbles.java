package dev.t3craft.client.hologram;

import com.mojang.math.Transformation;
import dev.t3craft.client.blocks.LampSignals;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.hq.HqPresence;
import dev.t3craft.client.npc.NpcInfo;
import dev.t3craft.client.npc.NpcLabels;
import dev.t3craft.client.npc.WorldText;
import dev.t3craft.client.workstation.StationStatus;
import dev.t3craft.client.workstation.WorkstationThread;
import dev.t3craft.hq.plan.Anchor;
import dev.t3craft.hq.plan.AnchorNames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * World text (docs/features/world-text.md): a billboard bubble over each decision podium
 * ({@link AnchorNames#PODIUM}{@code i}) only while a decision waits there, gone at rest. Podium
 * {@code i} stands for the i-th thread that needs the player ({@link LampSignals#urgent}, the same
 * order its lamp and its click use), so the bubble reads: line 1 the count waiting and the agent,
 * line 2 the question (the thread's pending request / last activity).
 *
 * <p>Hooked from the hologram side: it reads the HQ plan's anchors and the NPC roster, never the
 * podium block or its block entity, so it also shows over the plain lectern of an HQ without the
 * blocks module. A podium bound by hand to something else ({@code positional} bindings of the blocks
 * module) still shows its default {@code board:<i>} thread here.
 *
 * <p><b>Entity ids</b> inside the hologram's range ({@link dev.t3craft.client.ClientEntityIds#HOLOGRAM}),
 * in their own band from {@link #ID_BASE}. Text is pushed only when a podium's {@link Text} changes;
 * positions are recomputed only when the HQ standing changes.
 */
final class PodiumBubbles {
	static final int ID_BASE = -8_200_000;
	private static final int ID_BAND = 100_000;
	/** Height of the bubble's bottom above the podium cell's floor. */
	static final double LIFT = 1.55;
	static final float SCALE = 0.55f;
	static final int LINE_WIDTH_PX = 260;
	/** The question line is cut to this many code points. */
	static final int QUESTION_MAX = 48;
	/** The agent's name is cut to this many code points. */
	static final int AGENT_MAX = 28;

	/** What one podium's bubble says. */
	record Text(int waiting, String agent, String question, boolean error) {
	}

	private final Map<Integer, Display.TextDisplay> shown = new HashMap<>();
	private final Map<Integer, Text> texts = new HashMap<>();
	private @Nullable ChatTheme theme;
	private HqPresence.@Nullable Standing standing;
	/** Podium index → world cell, for the current standing. */
	private Map<Integer, BlockPos> podiums = Map.of();
	private int nextId = ID_BASE;
	private int ticks;
	/** Re-read the roster this often (ticks); a removed bubble or a new standing refreshes at once. */
	static final int REFRESH_TICKS = 5;

	/**
	 * Podium {@code index}'s text, or null when nobody waits for it. Pure.
	 *
	 * @param urgent the threads that need the player, most urgent first ({@link LampSignals#urgent})
	 * @param byKey  the roster, for the question
	 */
	static @Nullable Text text(int index, List<WorkstationThread> urgent, Map<String, NpcInfo> byKey) {
		if (index < 0 || index >= urgent.size()) {
			return null;
		}
		WorkstationThread t = urgent.get(index);
		NpcInfo info = byKey.get(t.key());
		String activity = info != null ? info.activity() : t.activity();
		boolean error = t.status() == StationStatus.ERROR;
		String q = NpcLabels.pillLine(activity, null);
		if (q.isEmpty()) {
			q = error ? "Something went wrong" : "Waiting for your decision";
		}
		return new Text(urgent.size(), NpcLabels.truncate(t.title() == null ? "" : t.title().strip(), AGENT_MAX),
				NpcLabels.truncate(q, QUESTION_MAX), error);
	}

	static Component component(Text t, ChatTheme theme) {
		int accent = (t.error() ? theme.error() : theme.needsYou()) & 0xFFFFFF;
		String count = t.waiting() == 1 ? "1 waiting" : t.waiting() + " waiting";
		return Component.literal("! ").withColor(accent)
				.append(Component.literal(count + " · ").withColor(theme.muted() & 0xFFFFFF))
				.append(Component.literal(t.agent()).withColor(theme.text() & 0xFFFFFF))
				.append(Component.literal("\n" + t.question()).withColor(theme.muted() & 0xFFFFFF));
	}

	/** Podium index → its world cell, for the standing HQ (only modules that stand). */
	static Map<Integer, BlockPos> podiums(HqPresence.Standing s) {
		Map<Integer, BlockPos> out = new TreeMap<>();
		for (Map.Entry<String, Anchor> e : s.plan().anchors().entrySet()) {
			if (!e.getKey().startsWith(AnchorNames.PODIUM)) {
				continue;
			}
			int i;
			try {
				i = Integer.parseInt(e.getKey().substring(AnchorNames.PODIUM.length()));
			} catch (NumberFormatException ex) {
				continue;
			}
			if (!HologramClient.standing(s, e.getValue())) {
				continue;
			}
			out.put(i, s.site().cell(e.getValue().block()));
		}
		return out;
	}

	/** One client tick: {@code roster} = every thread the client knows; null standing or off removes everything. */
	void tick(ClientLevel lvl, HqPresence.@Nullable Standing s, List<NpcInfo> roster, boolean on) {
		if (!on || s == null) {
			clear(lvl);
			return;
		}
		if (s != standing) {
			clear(lvl);
			standing = s;
			podiums = podiums(s);
			ticks = 0;
		}
		if (podiums.isEmpty()) {
			return;
		}
		boolean lost = false;
		for (Display.TextDisplay d : shown.values()) {
			lost |= d.isRemoved();
		}
		if (ticks++ % REFRESH_TICKS != 0 && !lost && theme == WorldText.theme()) {
			return;
		}
		List<WorkstationThread> threads = new ArrayList<>(roster.size());
		Map<String, NpcInfo> byKey = new HashMap<>();
		for (NpcInfo n : roster) {
			WorkstationThread t = WorkstationThread.fromNpc(n);
			threads.add(t);
			byKey.put(t.key(), n);
		}
		List<WorkstationThread> urgent = LampSignals.urgent(threads);
		ChatTheme th = WorldText.theme();
		boolean restyled = th != theme;
		theme = th;
		for (Map.Entry<Integer, BlockPos> e : podiums.entrySet()) {
			int i = e.getKey();
			Text want = text(i, urgent, byKey);
			Display.TextDisplay d = shown.get(i);
			if (want == null) {
				if (d != null) {
					remove(lvl, d);
					shown.remove(i);
					texts.remove(i);
				}
				continue;
			}
			boolean fresh = d == null || d.isRemoved();
			if (fresh) {
				BlockPos c = e.getValue();
				d = spawn(lvl, c.getX() + 0.5, c.getY() + LIFT, c.getZ() + 0.5);
				shown.put(i, d);
			}
			if (fresh || restyled || !want.equals(texts.get(i))) {
				d.setText(component(want, th));
				d.setBackgroundColor(ChatTheme.withAlpha(th.panel(), WorldText.BUBBLE_ALPHA));
				dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SET_TEXT);
				texts.put(i, want);
			}
		}
	}

	private Display.TextDisplay spawn(ClientLevel lvl, double x, double y, double z) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, lvl);
		int id = nextId--;
		if (nextId <= ID_BASE - ID_BAND) {
			nextId = ID_BASE;
		}
		d.setId(id);
		d.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		d.setLineWidth(LINE_WIDTH_PX);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.setTransformation(new Transformation(null, null, new Vector3f(SCALE), null));
		d.snapTo(x, y, z, 0f, 0f);
		lvl.addEntity(d);
		dev.t3craft.debug.PerfStats.count(dev.t3craft.debug.PerfStats.DISPLAY_SPAWNED);
		return d;
	}

	/** Removes every bubble ({@code lvl} null = the level is already gone). */
	void clear(@Nullable ClientLevel lvl) {
		shown.values().forEach(d -> remove(lvl, d));
		shown.clear();
		texts.clear();
		standing = null;
		podiums = Map.of();
	}

	private static void remove(@Nullable ClientLevel lvl, Entity e) {
		if (lvl != null && !e.isRemoved()) {
			lvl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
		}
	}
}
