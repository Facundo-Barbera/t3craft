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
 * Puts the archive in the world with client-only entities (never known to any server, nothing
 * registered, no block written):
 * <ul>
 *   <li>per occupied slot, a FIXED {@link Display.TextDisplay} label on the computer's screen face
 *   ({@link ArchiveLabel}: title, environment in its colour, close date, project), full-bright;</li>
 *   <li>while a slot's cell is air (HQ not built there), an {@link Interaction} hitbox so it can still
 *   be clicked; over a real block the click goes through {@code UseBlockCallback};</li>
 *   <li>the catalog: a floating title, and while its cell is air a {@link Display.BlockDisplay} prop
 *   (a vanilla lectern, or the block the hook gives) plus a hitbox.</li>
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
	static final String CATALOG = "#catalog";
	private static final float LABEL_SCALE = 0.18f;
	private static final int LABEL_LINE_PX = 140;
	private static final int LABEL_BACKGROUND = 0xCC101418;
	/** Label bottom below the face centre (four lines at {@link #LABEL_SCALE} ≈ 0.2 block tall). */
	private static final double LABEL_DROP = 0.11;
	private static final double FACE_OFFSET = 0.02;
	private static final int BLOCK_CHECK_TICKS = 20;

	/** What one slot should show. */
	record SlotView(String key, BlockPos cell, Anchor face, List<ArchiveLabel.Line> lines) {
	}

	/** What the catalog should show. */
	record CatalogView(BlockPos cell, Direction facing, String title, String subtitle, BlockState prop) {
	}

	private static final class Slot {
		final SlotView view;
		final Display.TextDisplay label;
		@Nullable Interaction hitbox;

		Slot(SlotView view, Display.TextDisplay label) {
			this.view = view;
			this.label = label;
		}

		boolean broken() {
			return label.isRemoved() || (hitbox != null && hitbox.isRemoved());
		}
	}

	private static final class Catalog {
		final CatalogView view;
		final Display.TextDisplay title;
		Display.@Nullable BlockDisplay prop;
		@Nullable Interaction hitbox;

		Catalog(CatalogView view, Display.TextDisplay title) {
			this.view = view;
			this.title = title;
		}

		boolean broken() {
			return title.isRemoved() || (prop != null && prop.isRemoved()) || (hitbox != null && hitbox.isRemoved());
		}
	}

	private final Map<String, Slot> slots = new LinkedHashMap<>();
	private final Map<Long, String> byCell = new HashMap<>();
	private final Map<Integer, String> byEntity = new HashMap<>();
	private @Nullable Catalog catalog;
	private int nextId = ID_BASE;
	private int tick;

	/** Entry key (or {@link #CATALOG}) whose cell is {@code pos}, or null. */
	@Nullable String at(BlockPos pos) {
		return byCell.get(pos.asLong());
	}

	/** Entry key (or {@link #CATALOG}) of one of our entities, or null. */
	@Nullable String of(Entity e) {
		return byEntity.get(e.getId());
	}

	int size() {
		return slots.size();
	}

	void tick(ClientLevel lvl, List<SlotView> wanted, @Nullable CatalogView cat) {
		tick++;
		boolean checkBlocks = tick % BLOCK_CHECK_TICKS == 0;
		Map<String, SlotView> next = new LinkedHashMap<>();
		wanted.forEach(v -> next.put(v.key(), v));
		for (String key : new ArrayList<>(slots.keySet())) {
			Slot s = slots.get(key);
			SlotView v = next.get(key);
			if (v == null || !v.cell().equals(s.view.cell()) || !v.face().equals(s.view.face()) || s.broken()) {
				discard(lvl, slots.remove(key));
			} else if (!v.lines().equals(s.view.lines())) {
				Slot updated = new Slot(v, s.label);
				updated.hitbox = s.hitbox;
				updated.label.setText(component(v.lines()));
				slots.put(key, updated);
			}
		}
		for (SlotView v : wanted) {
			Slot s = slots.get(v.key());
			boolean created = false;
			if (s == null) {
				s = spawn(lvl, v);
				slots.put(v.key(), s);
				created = true;
			}
			if (created || checkBlocks) {
				syncHitbox(lvl, s);
			}
		}
		syncCatalog(lvl, cat, checkBlocks);
	}

	void clear(@Nullable ClientLevel lvl) {
		for (Slot s : slots.values()) {
			discard(lvl, s);
		}
		slots.clear();
		discardCatalog(lvl);
		byCell.clear();
		byEntity.clear();
	}

	// ---- slots --------------------------------------------------------------------------------

	private Slot spawn(ClientLevel lvl, SlotView v) {
		Display.TextDisplay d = text(lvl, LABEL_SCALE, LABEL_LINE_PX, Display.BillboardConstraints.FIXED);
		d.setBackgroundColor(LABEL_BACKGROUND);
		d.setText(component(v.lines()));
		Direction f = v.face().facing();
		d.snapTo(v.face().x() + f.getStepX() * FACE_OFFSET, v.face().y() - LABEL_DROP, v.face().z() + f.getStepZ() * FACE_OFFSET, f.toYRot(), 0f);
		lvl.addEntity(d);
		Slot s = new Slot(v, d);
		byCell.put(v.cell().asLong(), v.key());
		byEntity.put(d.getId(), v.key());
		return s;
	}

	private void syncHitbox(ClientLevel lvl, Slot s) {
		boolean air = lvl.getBlockState(s.view.cell()).isAir();
		if (air && s.hitbox == null) {
			s.hitbox = hitbox(lvl, s.view.cell(), 0.9f, 0.9f, s.view.key());
		} else if (!air && s.hitbox != null) {
			byEntity.remove(s.hitbox.getId());
			remove(lvl, s.hitbox);
			s.hitbox = null;
		}
	}

	private void discard(@Nullable ClientLevel lvl, @Nullable Slot s) {
		if (s == null) {
			return;
		}
		byEntity.remove(s.label.getId());
		remove(lvl, s.label);
		if (s.hitbox != null) {
			byEntity.remove(s.hitbox.getId());
			remove(lvl, s.hitbox);
		}
		byCell.remove(s.view.cell().asLong(), s.view.key());
	}

	// ---- catalog ------------------------------------------------------------------------------

	private void syncCatalog(ClientLevel lvl, @Nullable CatalogView v, boolean checkBlocks) {
		Catalog c = catalog;
		if (v == null) {
			discardCatalog(lvl);
			return;
		}
		boolean moved = c == null || !c.view.cell().equals(v.cell()) || c.view.facing() != v.facing() || !c.view.prop().equals(v.prop());
		if (c != null && (moved || c.broken())) {
			discardCatalog(lvl);
			c = null;
		}
		if (c == null) {
			Display.TextDisplay t = text(lvl, 0.5f, 200, Display.BillboardConstraints.VERTICAL);
			t.snapTo(v.cell().getX() + 0.5, v.cell().getY() + 1.45, v.cell().getZ() + 0.5, 0f, 0f);
			lvl.addEntity(t);
			c = new Catalog(v, t);
			catalog = c;
			byCell.put(v.cell().asLong(), CATALOG);
			byEntity.put(t.getId(), CATALOG);
			t.setText(catalogText(v));
			checkBlocks = true;
		} else if (!Objects.equals(c.view.title(), v.title()) || !Objects.equals(c.view.subtitle(), v.subtitle())) {
			Catalog u = new Catalog(v, c.title);
			u.prop = c.prop;
			u.hitbox = c.hitbox;
			u.title.setText(catalogText(v));
			catalog = c = u;
		}
		if (checkBlocks) {
			boolean air = lvl.getBlockState(v.cell()).isAir();
			if (air && c.prop == null) {
				Display.BlockDisplay prop = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, lvl);
				prop.setId(nextId--);
				prop.setBlockState(v.prop());
				prop.snapTo(v.cell().getX(), v.cell().getY(), v.cell().getZ(), 0f, 0f);
				lvl.addEntity(prop);
				c.prop = prop;
				byEntity.put(prop.getId(), CATALOG);
				c.hitbox = hitbox(lvl, v.cell(), 1.0f, 1.0f, CATALOG);
			} else if (!air && c.prop != null) {
				byEntity.remove(c.prop.getId());
				remove(lvl, c.prop);
				c.prop = null;
				if (c.hitbox != null) {
					byEntity.remove(c.hitbox.getId());
					remove(lvl, c.hitbox);
					c.hitbox = null;
				}
			}
		}
	}

	private void discardCatalog(@Nullable ClientLevel lvl) {
		Catalog c = catalog;
		catalog = null;
		if (c == null) {
			return;
		}
		for (Entity e : new Entity[] {c.title, c.prop, c.hitbox}) {
			if (e != null) {
				byEntity.remove(e.getId());
				remove(lvl, e);
			}
		}
		byCell.remove(c.view.cell().asLong(), CATALOG);
	}

	private static Component catalogText(CatalogView v) {
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

	private Interaction hitbox(ClientLevel lvl, BlockPos cell, float width, float height, String key) {
		Interaction hit = new Interaction(EntityTypes.INTERACTION, lvl);
		hit.setId(nextId--);
		hit.setWidth(width);
		hit.setHeight(height);
		hit.snapTo(cell.getX() + 0.5, cell.getY() + (1.0 - height) / 2, cell.getZ() + 0.5, 0f, 0f);
		lvl.addEntity(hit);
		byEntity.put(hit.getId(), key);
		return hit;
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
