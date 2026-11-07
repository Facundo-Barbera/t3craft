package dev.t3craft.client.archive;

import com.mojang.math.Transformation;
import dev.t3craft.hq.Anchor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Puts the "Archived" shelf in the world with client-only entities (never known to any server,
 * nothing registered, no block written):
 * <ul>
 *   <li>per occupied slot, a FIXED {@link Display.TextDisplay} label on the slot's face
 *   ({@link ArchiveLabel}: title, environment in its colour, close date, project), full-bright;</li>
 *   <li>per slot whose cell is air (the HQ has no block there yet, e.g. the fallback shelf), a
 *   {@link Display.BlockDisplay} prop and an {@link Interaction} hitbox; over a real block neither
 *   exists and the click goes through {@code UseBlockCallback};</li>
 *   <li>the shelf's title over the slots, with a hitbox: clicking it (or an empty slot) opens the
 *   catalog.</li>
 * </ul>
 *
 * <p><b>Entity ids</b> count down from {@link #ID_BASE} = −6,000,000. Taken ranges: NpcRegistry
 * −1,000,000…, builders −2,000,000…, BehaviorDirector −3,000,000…, npc helpers −4,000,000…,
 * workstations −5,000,000…. NpcRegistry's orphan sweep treats every id ≤ −1,000,000 as its own and
 * may discard ours after one of its rare recoveries: each tick checks {@link Entity#isRemoved()} and
 * rebuilds what vanished. Entities are kept by reference, never looked up by id with a cast.
 */
final class ArchiveRenderer {
	static final int ID_BASE = -6_000_000;
	/** Click target of the title and of empty slots: opens the catalog. */
	static final String CATALOG = "#catalog";
	private static final float LABEL_SCALE = 0.18f;
	private static final int LABEL_LINE_PX = 140;
	private static final int LABEL_BACKGROUND = 0xCC101418;
	/** Label bottom below the face centre (four lines at {@link #LABEL_SCALE} ≈ 0.2 block tall). */
	private static final double LABEL_DROP = 0.11;
	private static final double FACE_OFFSET = 0.02;
	private static final int BLOCK_CHECK_TICKS = 20;

	/**
	 * What one slot should show. {@code key} is the entry key, or null for an empty slot;
	 * {@code lines} is empty for an empty slot; {@code prop} is drawn only while the cell is air.
	 */
	record SlotView(int slot, @Nullable String key, BlockPos cell, Anchor face, List<ArchiveLabel.Line> lines, BlockState prop) {
		String target() {
			return key == null ? CATALOG : key;
		}
	}

	/** The shelf's title (world anchor: point, facing). */
	record HeaderView(Anchor at, String title, String subtitle) {
	}

	private static final class Slot {
		SlotView view;
		Display.@Nullable TextDisplay label;
		Display.@Nullable BlockDisplay prop;
		@Nullable Interaction hitbox;

		Slot(SlotView view) {
			this.view = view;
		}

		boolean broken() {
			return (label != null && label.isRemoved()) || (prop != null && prop.isRemoved()) || (hitbox != null && hitbox.isRemoved());
		}
	}

	private static final class Header {
		final HeaderView view;
		final Display.TextDisplay text;
		final Interaction hitbox;

		Header(HeaderView view, Display.TextDisplay text, Interaction hitbox) {
			this.view = view;
			this.text = text;
			this.hitbox = hitbox;
		}
	}

	private final Map<Integer, Slot> slots = new LinkedHashMap<>();
	private final Map<Long, Integer> byCell = new HashMap<>();
	private final Map<Integer, Integer> byEntity = new HashMap<>();
	private @Nullable Header header;
	private int nextId = ID_BASE;
	private int tick;

	/** Click target (entry key or {@link #CATALOG}) of the slot whose cell is {@code pos}, or null. */
	@Nullable String at(BlockPos pos) {
		Integer s = byCell.get(pos.asLong());
		Slot slot = s == null ? null : slots.get(s);
		return slot == null ? null : slot.view.target();
	}

	/** Click target of one of our entities, or null. */
	@Nullable String of(Entity e) {
		Header h = header;
		if (h != null && (e == h.text || e == h.hitbox)) {
			return CATALOG;
		}
		Integer s = byEntity.get(e.getId());
		Slot slot = s == null ? null : slots.get(s);
		return slot == null ? null : slot.view.target();
	}

	int size() {
		return slots.size();
	}

	void tick(ClientLevel lvl, List<SlotView> wanted, @Nullable HeaderView head) {
		tick++;
		boolean checkBlocks = tick % BLOCK_CHECK_TICKS == 0;
		Map<Integer, SlotView> next = new LinkedHashMap<>();
		wanted.forEach(v -> next.put(v.slot(), v));
		for (Integer i : new ArrayList<>(slots.keySet())) {
			Slot s = slots.get(i);
			SlotView v = next.get(i);
			if (v == null || !v.cell().equals(s.view.cell()) || !v.face().equals(s.view.face()) || s.broken()) {
				discard(lvl, slots.remove(i));
			}
		}
		for (SlotView v : wanted) {
			Slot s = slots.get(v.slot());
			boolean created = s == null;
			if (created) {
				s = new Slot(v);
				slots.put(v.slot(), s);
				byCell.put(v.cell().asLong(), v.slot());
			}
			SlotView old = s.view;
			s.view = v;
			if (created || !old.lines().equals(v.lines())) {
				syncLabel(lvl, s);
			}
			if (created || checkBlocks || !old.prop().equals(v.prop())) {
				syncProp(lvl, s, !old.prop().equals(v.prop()));
			}
		}
		syncHeader(lvl, head);
	}

	void clear(@Nullable ClientLevel lvl) {
		for (Slot s : slots.values()) {
			discard(lvl, s);
		}
		slots.clear();
		discardHeader(lvl);
		byCell.clear();
		byEntity.clear();
	}

	// ---- slots --------------------------------------------------------------------------------

	private void syncLabel(ClientLevel lvl, Slot s) {
		SlotView v = s.view;
		if (v.lines().isEmpty()) {
			if (s.label != null) {
				forget(lvl, s.label);
				s.label = null;
			}
			return;
		}
		if (s.label == null) {
			Display.TextDisplay d = text(lvl, LABEL_SCALE, LABEL_LINE_PX, Display.BillboardConstraints.FIXED);
			d.setBackgroundColor(LABEL_BACKGROUND);
			Direction f = v.face().facing();
			d.snapTo(v.face().x() + f.getStepX() * FACE_OFFSET, v.face().y() - LABEL_DROP, v.face().z() + f.getStepZ() * FACE_OFFSET, f.toYRot(), 0f);
			lvl.addEntity(d);
			byEntity.put(d.getId(), v.slot());
			s.label = d;
		}
		s.label.setText(component(v.lines()));
	}

	/** Prop + hitbox only while the cell is air; {@code restate}: the prop's block state changed. */
	private void syncProp(ClientLevel lvl, Slot s, boolean restate) {
		boolean air = lvl.getBlockState(s.view.cell()).isAir();
		if (air) {
			if (s.prop == null) {
				Display.BlockDisplay prop = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, lvl);
				prop.setId(nextId--);
				prop.setBlockState(s.view.prop());
				prop.snapTo(s.view.cell().getX(), s.view.cell().getY(), s.view.cell().getZ(), 0f, 0f);
				lvl.addEntity(prop);
				byEntity.put(prop.getId(), s.view.slot());
				s.prop = prop;
			} else if (restate) {
				s.prop.setBlockState(s.view.prop());
			}
			if (s.hitbox == null) {
				Interaction hit = hitbox(lvl, s.view.cell().getX() + 0.5, s.view.cell().getY() + 0.05, s.view.cell().getZ() + 0.5, 0.9f, 0.9f);
				byEntity.put(hit.getId(), s.view.slot());
				s.hitbox = hit;
			}
		} else {
			if (s.prop != null) {
				forget(lvl, s.prop);
				s.prop = null;
			}
			if (s.hitbox != null) {
				forget(lvl, s.hitbox);
				s.hitbox = null;
			}
		}
	}

	private void discard(@Nullable ClientLevel lvl, @Nullable Slot s) {
		if (s == null) {
			return;
		}
		for (Entity e : new Entity[] {s.label, s.prop, s.hitbox}) {
			if (e != null) {
				forget(lvl, e);
			}
		}
		byCell.remove(s.view.cell().asLong(), s.view.slot());
	}

	// ---- header -------------------------------------------------------------------------------

	private void syncHeader(ClientLevel lvl, @Nullable HeaderView v) {
		Header h = header;
		if (v == null) {
			discardHeader(lvl);
			return;
		}
		if (h != null && (!h.view.at().equals(v.at()) || h.text.isRemoved() || h.hitbox.isRemoved())) {
			discardHeader(lvl);
			h = null;
		}
		if (h == null) {
			Display.TextDisplay t = text(lvl, 0.4f, 240, Display.BillboardConstraints.FIXED);
			t.setBackgroundColor(LABEL_BACKGROUND);
			Anchor a = v.at();
			t.snapTo(a.x() + a.facing().getStepX() * FACE_OFFSET, a.y(), a.z() + a.facing().getStepZ() * FACE_OFFSET, a.facing().toYRot(), 0f);
			lvl.addEntity(t);
			Interaction hit = hitbox(lvl, a.x() + a.facing().getStepX() * 0.3, a.y(), a.z() + a.facing().getStepZ() * 0.3, 1.6f, 0.5f);
			header = h = new Header(v, t, hit);
			t.setText(headerText(v));
		} else if (!Objects.equals(h.view.title(), v.title()) || !Objects.equals(h.view.subtitle(), v.subtitle())) {
			h.text.setText(headerText(v));
			header = new Header(v, h.text, h.hitbox);
		}
	}

	private void discardHeader(@Nullable ClientLevel lvl) {
		Header h = header;
		header = null;
		if (h != null) {
			remove(lvl, h.text);
			remove(lvl, h.hitbox);
		}
	}

	private static Component headerText(HeaderView v) {
		return Component.literal(v.title()).withColor(0xFFD27F).append(Component.literal("\n" + v.subtitle()).withColor(0xBBBBBB));
	}

	// ---- helpers ------------------------------------------------------------------------------

	private Display.TextDisplay text(ClientLevel lvl, float scale, int lineWidth, Display.BillboardConstraints billboard) {
		Display.TextDisplay d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, lvl);
		d.setId(nextId--);
		d.setBillboardConstraints(billboard);
		d.setLineWidth(lineWidth);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.setTransformation(new Transformation(null, null, new Vector3f(scale), null));
		return d;
	}

	private Interaction hitbox(ClientLevel lvl, double x, double y, double z, float width, float height) {
		Interaction hit = new Interaction(EntityTypes.INTERACTION, lvl);
		hit.setId(nextId--);
		hit.setWidth(width);
		hit.setHeight(height);
		hit.snapTo(x, y, z, 0f, 0f);
		lvl.addEntity(hit);
		return hit;
	}

	private void forget(@Nullable ClientLevel lvl, Entity e) {
		byEntity.remove(e.getId());
		remove(lvl, e);
	}

	private static void remove(@Nullable ClientLevel lvl, Entity e) {
		if (lvl != null && !e.isRemoved()) {
			lvl.removeEntity(e.getId(), Entity.RemovalReason.DISCARDED);
		}
	}

	static Component component(List<ArchiveLabel.Line> lines) {
		MutableComponent out = Component.empty();
		boolean first = true;
		for (ArchiveLabel.Line l : lines) {
			if (!first) {
				out.append("\n");
			}
			out.append(Component.literal(l.text()).withColor(l.color()));
			first = false;
		}
		return out;
	}
}
