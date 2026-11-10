package dev.t3craft.client.demo;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.mapgen.MapTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import org.jspecify.annotations.Nullable;

/**
 * The HQ menu as a page of the UI kit (docs/features/hq-menu.md, docs/features/ui-kit.md), shown by
 * {@link HqMenuScreen} over the title or world-select screen:
 * <ul>
 *   <li><b>Map type</b>: a {@link T3Select} over the {@code MapTypes} registry (a type registered later shows up
 *       by itself) with the chosen type's name and description. The choice is saved to {@code mapType}
 *       ({@link MapTypeModel}). Each type has its own world, so choosing never regenerates anything: it decides
 *       which world Open goes to. The layout list, Export / Reload, Rebuild and Reset below apply to the
 *       citadel's HQ world and are off for the other types.</li>
 *   <li><b>Open HQ</b> (header): create or open the world of the chosen map type (the HQ world for the citadel)
 *       and enter. When a world is open the demo asks itself before leaving it.</li>
 *   <li><b>HQ layout</b>: the {@code HqLayouts} registry in a {@link T3List} (name, one line of description,
 *       tags BUILT / MAIN / VERY LARGE). A left click, or Enter on the keyboard cursor, chooses; the choice is
 *       saved to {@code hqLayout} at once ({@link HqMenuModel}).</li>
 *   <li><b>Export / Reload layouts</b> (config/t3craft/hq-layouts), <b>Rebuild with &lt;layout&gt;</b> (only
 *       when the HQ is built with another layout) and <b>Reset HQ</b> (footer). Rebuild and Reset ask first
 *       with a modal over the page, then run the existing reset (zip backup, delete only the HQ folder,
 *       create it again) with the chosen layout.</li>
 * </ul>
 */
public final class HqMenuPanel extends T3Panel {
	/** What the menu does outside itself; {@link HqMenuScreen} wires the demo, the gallery a fake. */
	public interface Actions {
		/** Whether the menu can open the world of {@code type}; if not, Open is off and names the type's command. */
		boolean canOpen(MapTypes type);

		/** Opens (or creates) the world of {@code type} (the HQ for the citadel); {@code parent} is the screen to come back to. */
		void open(Screen parent, MapTypes type);

		/** Why a reset cannot run now, or null. */
		@Nullable String refusal();

		/** Body of the reset / rebuild question for {@code layout} (backup or not, which folder). */
		String resetBody(HqLayoutInfo layout);

		/** Saves the layout and queues the confirmed reset; a refusal, or null. */
		@Nullable String resetAndRebuild(String layoutId);

		/** Export layouts: the result line. */
		String exportPacks();

		/** Reload layouts: the result line. */
		String reloadPacks();
	}

	private static final int ROW_H = 24;
	private static final int GAP = 4;

	private final HqMenuModel model;
	private final MapTypeModel maps;
	private final Actions actions;
	private final T3List<Integer> list;
	/** Result of the last Export / Reload press, shown on the info line (null: the HQ status). */
	private @Nullable String packStatus;
	private boolean cursorPlaced;

	public HqMenuPanel(HqMenuModel model, MapTypeModel maps, Actions actions) {
		this.model = model;
		this.maps = maps;
		this.actions = actions;
		this.list = new T3List<>(ROW_H, new T3List.Row<Integer>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, Integer i, Rect r, int mx, int my) {
				drawRow(g, font, t, i, r);
			}

			@Override
			public boolean mouseClicked(Integer i, Rect r, double mx, double my, int button) {
				if (!MouseButtons.isLeft(button)) {
					return false; // only a left click chooses a layout (26.x: left = 1)
				}
				choose(i);
				return true;
			}

			@Override
			public void activated(Integer i) {
				choose(i);
			}
		});
	}

	@Override
	public String title() {
		return T3Draw.tr("t3craft.hqmenu.title");
	}

	@Override
	protected int headerHeight() {
		return T3Control.H + 6;
	}

	@Override
	protected int footerHeight() {
		return 2 * T3Control.H + GAP + 6;
	}

	// ---- layout -------------------------------------------------------------------------------------------

	@Override
	protected void arrangeHeader(Rect header) {
		MapTypes type = maps.selected();
		boolean citadel = maps.hqApplies();
		String label = citadel ? T3Draw.tr("t3craft.hqmenu.open") : T3Draw.tr("t3craft.hqmenu.open_map", typeName(type));
		T3Button open = new T3Button(T3Icon.PLAY, label, T3Button.Style.PRIMARY, () -> actions.open(host().screen(), type));
		if (!actions.canOpen(type)) {
			open.setEnabled(false);
			open.setTooltip(T3Draw.tr("t3craft.hqmenu.map.no_opener", type.openWith()));
		} else {
			open.setTooltip(T3Draw.tr(citadel ? "t3craft.demo.button.tooltip" : "t3craft.hqmenu.map.open.tooltip", typeName(type)));
		}
		pin(open, header.x(), header.y(), header.w(), T3Control.H);
	}

	@Override
	protected int arrange(Rect body, int y0) {
		MapTypes type = maps.selected();
		int y = text(body.x(), y0 + 2, body.w(), T3Draw.tr("t3craft.hqmenu.map"), Tone.MUTED) + 2;
		T3Select<MapTypes> select = new T3Select<>(maps.types(), HqMenuPanel::typeName, type, chosen -> {
			if (maps.select(chosen)) {
				packStatus = null;
				host().relayout();
			}
		});
		select.setTooltip(T3Draw.tr("t3craft.hqmenu.map.tooltip"));
		place(select, body.x(), y, body.w(), T3Control.H);
		y += T3Control.H + 3;
		y = text(body.x(), y, body.w(), typeSummary(type), Tone.NORMAL) + 6;
		if (!maps.hqApplies()) {
			cursorPlaced = false; // the layout list is not placed: scroll it to the chosen layout the next time it is
			return notice(body.x(), y, body.w(), T3Draw.tr("t3craft.hqmenu.map.own_world", typeName(type)), Tone.NORMAL);
		}
		y = text(body.x(), y, body.w(), T3Draw.tr("t3craft.hqmenu.layout"), Tone.MUTED) + 2;
		int lh = T3Draw.lineHeight(font());
		int listH = Math.max(2 * ROW_H + 4, body.bottom() - y - lh - 4);
		List<Integer> rows = new ArrayList<>();
		for (int i = 0; i < model.size(); i++) {
			rows.add(i);
		}
		list.setItems(rows);
		place(list, body.x(), y, body.w(), listH);
		if (!cursorPlaced) {
			cursorPlaced = true;
			list.setCursor(model.selectedIndex()); // shows the chosen layout, scrolled into view
		}
		y += listH + 4;
		return text(body.x(), y, body.w(), ChatDraw.clip(font(), info(), body.w()), packStatus != null ? Tone.NORMAL : Tone.MUTED);
	}

	@Override
	protected void arrangeFooter(Rect footer) {
		boolean hq = maps.hqApplies(); // layouts, Rebuild and Reset belong to the HQ world: only the citadel type has it
		int half = (footer.w() - GAP) / 2;
		int y = footer.y() + 4;
		T3Button export = new T3Button(T3Icon.DOWNLOAD, T3Draw.tr("t3craft.hqmenu.packs.export"), () -> {
			packStatus = actions.exportPacks();
			host().relayout();
		});
		export.setTooltip(T3Draw.tr(hq ? "t3craft.hqmenu.packs.export.tooltip" : "t3craft.hqmenu.hq_only"));
		export.setEnabled(hq);
		pin(export, footer.x(), y, half, T3Control.H);
		T3Button reload = new T3Button(T3Icon.REFRESH_CW, T3Draw.tr("t3craft.hqmenu.packs.reload"), () -> {
			packStatus = actions.reloadPacks();
			host().relayout();
		});
		reload.setTooltip(T3Draw.tr(hq ? "t3craft.hqmenu.packs.reload.tooltip" : "t3craft.hqmenu.hq_only"));
		reload.setEnabled(hq);
		pin(reload, footer.x() + half + GAP, y, footer.w() - half - GAP, T3Control.H);

		y += T3Control.H + GAP;
		T3Button reset = new T3Button(T3Icon.TRASH, T3Draw.tr("t3craft.hqmenu.reset"), T3Button.Style.DANGER, () -> confirmReset(false));
		reset.setTooltip(T3Draw.tr(hq ? "t3craft.hqmenu.reset.tooltip" : "t3craft.hqmenu.hq_only"));
		reset.setEnabled(hq && model.resetOffered());
		if (hq && model.rebuildOffered()) {
			String name = T3Draw.tr(model.selected().nameKey());
			pin(new T3Button(T3Icon.REFRESH_CW, T3Draw.tr("t3craft.hqmenu.rebuild", name), () -> confirmReset(true)), footer.x(), y, half, T3Control.H);
			pin(reset, footer.x() + half + GAP, y, footer.w() - half - GAP, T3Control.H);
		} else {
			pin(reset, footer.x(), y, footer.w(), T3Control.H);
		}
	}

	// ---- actions ------------------------------------------------------------------------------------------

	private void choose(int i) {
		packStatus = null;
		if (model.select(i)) {
			list.setCursor(i);
		}
		host().relayout();
	}

	/** Asks first; on yes runs the reset (zip backup, only the HQ folder) with the chosen layout. */
	private void confirmReset(boolean rebuildChosen) {
		String refusal = actions.refusal();
		if (refusal != null) {
			alert(refusal);
			return;
		}
		HqLayoutInfo layout = model.selected();
		String name = T3Draw.tr(layout.nameKey());
		String title = rebuildChosen ? T3Draw.tr("t3craft.hqmenu.rebuild.title", name) : T3Draw.tr("t3craft.demo.reset.title");
		String yes = rebuildChosen ? T3Draw.tr("t3craft.hqmenu.rebuild", name) : T3Draw.tr("t3craft.hqmenu.reset");
		confirm(title, List.of(actions.resetBody(layout)), null, yes, true, () -> {
			String why = actions.resetAndRebuild(layout.id()); // runs on the next client tick
			if (why != null) {
				alert(why);
			}
		});
	}

	/** A message with one OK button over the page. */
	private void alert(String message) {
		T3Button ok = new T3Button(CommonComponents.GUI_DONE.getString(), T3Button.Style.PRIMARY, () -> host().modal(null));
		host().modal(new T3Dialog(T3Draw.tr("t3craft.demo.reset.title"), 320)
				.text(message, Tone.ERROR)
				.gap(2)
				.buttons(ok)
				.onCancel(() -> host().modal(null))
				.defaultAction(() -> host().modal(null)));
	}

	// ---- map types ----------------------------------------------------------------------------------------

	/** The type's name: {@code t3craft.maptype.<id>.name} when the language has it, else the registry's own text. */
	static String typeName(MapTypes t) {
		return localized("t3craft.maptype." + t.id() + ".name", t.name());
	}

	/** The type's one-line description: {@code t3craft.maptype.<id>.desc} when there is one, else the registry's own text. */
	static String typeSummary(MapTypes t) {
		return localized("t3craft.maptype." + t.id() + ".desc", t.summary());
	}

	private static String localized(String key, String fallback) {
		String s = T3Draw.tr(key);
		return s.equals(key) ? fallback : s; // a missing key translates to itself
	}

	// ---- drawing ------------------------------------------------------------------------------------------

	private void drawRow(GuiGraphicsExtractor g, Font font, ChatTheme t, int i, Rect r) {
		HqLayoutInfo l = model.get(i);
		boolean sel = model.isSelected(i);
		if (sel) {
			g.fill(r.x(), r.y(), r.right(), r.bottom(), t.selected());
			g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), t.accent()); // the selection bar
		} else {
			g.fill(r.x() + 2, r.bottom() - 1, r.right() - 2, r.bottom(), t.edge());
		}
		int tx = r.x() + 6;
		int avail = r.right() - tx - 4;

		// tags from the right: [built] [main] [very large]
		List<String> tags = new ArrayList<>();
		List<Integer> colours = new ArrayList<>();
		if (model.isBuilt(i)) {
			tags.add(T3Draw.tr("t3craft.hqmenu.tag.built"));
			colours.add(t.result());
		}
		if (l.primary()) {
			tags.add(T3Draw.tr("t3craft.hqmenu.tag.primary"));
			colours.add(t.accent());
		}
		if (model.warnLarge(i)) {
			tags.add(T3Draw.tr("t3craft.hqmenu.tag.large"));
			colours.add(t.youText());
		}
		int tagsW = 0;
		int right = r.right() - 4;
		int top = r.y() + 3;
		for (int k = 0; k < tags.size(); k++) {
			int tw = font.width(tags.get(k));
			if (tagsW + tw + 6 > avail / 2) {
				continue; // narrow window: keep at least half the row for the name (the line below repeats the warning)
			}
			right -= tw;
			g.text(font, tags.get(k), right, top, colours.get(k), t.shadow());
			right -= 6;
			tagsW += tw + 6;
		}
		String name = T3Draw.tr(l.nameKey());
		g.text(font, ChatDraw.clip(font, name, avail - tagsW), tx, top, sel ? t.accent() : t.text(), t.shadow());
		String desc = T3Draw.tr(l.descKey());
		g.text(font, ChatDraw.clip(font, desc, avail), tx, top + font.lineHeight + 2, model.warnLarge(i) ? t.youText() : t.muted(), t.shadow());
	}

	private String info() {
		if (packStatus != null) {
			return packStatus;
		}
		String chosen = T3Draw.tr(model.selected().nameKey());
		if (!model.hqExists()) {
			return T3Draw.tr("t3craft.hqmenu.info.no_world", chosen);
		}
		if (model.builtId() == null) {
			return T3Draw.tr("t3craft.hqmenu.info.not_built", chosen);
		}
		HqLayoutInfo built = model.built();
		return T3Draw.tr("t3craft.hqmenu.info.built", built != null ? T3Draw.tr(built.nameKey()) : model.builtId());
	}
}
