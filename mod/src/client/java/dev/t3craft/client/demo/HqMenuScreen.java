package dev.t3craft.client.demo;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.hq.layout.HqLayoutInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The HQ menu, opened by the "T3Craft HQ" button of the title and world-select screens:
 * <ul>
 *   <li><b>Open HQ</b>: what the button used to do (create or open the HQ world and enter).</li>
 *   <li><b>Layout</b>: the {@code HqLayouts} registry as a scrolling list (name, one line of
 *       description, "very large" tag on a big layout, which one is chosen and which one is built). The
 *       main design ({@code HqLayouts.PRIMARY_ID}) comes first. A
 *       left click or the arrow keys choose; the choice is saved to {@code hqLayout} at once.</li>
 *   <li><b>Rebuild with &lt;layout&gt;</b> (only when the HQ is built with another layout) and
 *       <b>Reset HQ</b>: both ask first, then run the existing reset (zip backup, delete only
 *       {@code saves/<demo folder>}, create it again) with the chosen layout. See
 *       {@code docs/features/hq-menu.md} for why this runs from the menu with no world open.</li>
 * </ul>
 * Escape or Back returns to {@code parent}. All logic worth testing lives in {@link HqMenuModel},
 * {@link HqMenuList} and {@link HqMenuKeys}.
 */
public final class HqMenuScreen extends Screen {
	private static final int ROW_H = 24;
	private static final int MAX_W = 340;
	private static final int GAP = 4;

	private static final int ROW_SELECTED = 0x40FFFFFF;
	private static final int ROW_HOVER = 0x20FFFFFF;
	private static final int WELL = 0x80000000;
	private static final int BORDER = 0xFF555555;
	private static final int BORDER_SELECTED = 0xFFFFFFFF;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int MUTED = 0xFFAAAAAA;
	private static final int BUILT = 0xFF55FF55;
	private static final int LARGE = 0xFFFFAA00;
	private static final int PRIMARY = 0xFF55FFFF;

	private final @Nullable Screen parent;
	private final DemoClient demo;
	private final HqMenuModel model;
	private final HqMenuList list = new HqMenuList(0, 0, 0, 0, ROW_H, 0);
	private @Nullable Button rebuild;
	private @Nullable Button reset;
	private int infoY;
	/** Result of the last Export / Reload layouts press, shown on the info line (null: the HQ status). */
	private @Nullable String packStatus;

	HqMenuScreen(@Nullable Screen parent, DemoClient demo) {
		super(Component.translatable("t3craft.hqmenu.title"));
		this.parent = parent;
		this.demo = demo;
		this.model = HqMenuBridge.model(demo);
	}

	@Override
	protected void init() {
		int w = Math.min(MAX_W, width - 32);
		int x = (width - w) / 2;
		int y = 22;
		Button open = addRenderableWidget(Button.builder(Component.translatable("t3craft.hqmenu.open").withStyle(ChatFormatting.GREEN),
				b -> demo.open(minecraft, this, false))
				.bounds(x, y, w, 20)
				.tooltip(Tooltip.create(Component.translatable("t3craft.demo.button.tooltip")))
				.build());

		// Bottom up: [Reset HQ | Back], [Rebuild with …], one info line, then the list fills the rest.
		int backY = height - 24;
		int half = (w - GAP) / 2;
		reset = addRenderableWidget(Button.builder(Component.translatable("t3craft.hqmenu.reset").withStyle(ChatFormatting.RED),
				b -> confirmReset(false))
				.bounds(x, backY, half, 20)
				.tooltip(Tooltip.create(Component.translatable("t3craft.hqmenu.reset.tooltip")))
				.build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(x + half + GAP, backY, w - half - GAP, 20).build());
		int rebuildY = backY - 24;
		rebuild = addRenderableWidget(Button.builder(Component.empty(), b -> confirmReset(true)).bounds(x, rebuildY, w, 20).build());
		// feat/hq-layout-packs: [Export layouts | Reload layouts] (config/t3craft/hq-layouts, applied without restarting)
		int packsY = rebuildY - 24;
		addRenderableWidget(Button.builder(Component.translatable("t3craft.hqmenu.packs.export"),
				b -> packStatus = dev.t3craft.client.hq.layout.HqLayoutPackWatcher.exportNow())
				.bounds(x, packsY, half, 20)
				.tooltip(Tooltip.create(Component.translatable("t3craft.hqmenu.packs.export.tooltip")))
				.build());
		addRenderableWidget(Button.builder(Component.translatable("t3craft.hqmenu.packs.reload"),
				b -> packStatus = dev.t3craft.client.hq.layout.HqLayoutPackWatcher.reloadNow())
				.bounds(x + half + GAP, packsY, w - half - GAP, 20)
				.tooltip(Tooltip.create(Component.translatable("t3craft.hqmenu.packs.reload.tooltip")))
				.build());
		infoY = packsY - font.lineHeight - 4;

		int listTop = y + 20 + 6 + font.lineHeight + 3; // under "Open HQ" and the "Layout" header
		int listBottom = Math.max(listTop + ROW_H, infoY - 3); // at least one row, even at GUI scale 4 on a short window
		list.layout(x, listTop, w, listBottom - listTop, ROW_H, rowCount());
		ensureSelectedVisible();
		refreshButtons();
		setInitialFocus(open);
	}

	private void refreshButtons() {
		if (rebuild != null) {
			rebuild.visible = model.rebuildOffered();
			rebuild.setMessage(Component.translatable("t3craft.hqmenu.rebuild", Component.translatable(model.selected().nameKey()))
					.withStyle(ChatFormatting.GOLD));
		}
		if (reset != null) {
			reset.active = model.resetOffered();
		}
	}

	// ---- reset / rebuild --------------------------------------------------------------------

	/** Asks first; on yes runs the reset (zip backup, only the HQ folder) with the chosen layout. */
	private void confirmReset(boolean rebuildChosen) {
		@Nullable String refusal = HqMenuBridge.refusal();
		if (refusal != null) {
			minecraft.gui.setScreen(new AlertScreen(() -> minecraft.gui.setScreen(this), Component.translatable("t3craft.demo.reset.title"),
					Component.literal(refusal)));
			return;
		}
		HqLayoutInfo layout = model.selected();
		DemoConfig cfg = demo.loadConfig();
		Component name = Component.translatable(layout.nameKey());
		Component title = rebuildChosen
				? Component.translatable("t3craft.hqmenu.rebuild.title", name)
				: Component.translatable("t3craft.demo.reset.title");
		Component body = Component.translatable(HqReset.shouldBackup(cfg) ? "t3craft.hqmenu.reset.body" : "t3craft.hqmenu.reset.body_nobackup",
				cfg.spec().displayName(), cfg.spec().folderId(), name);
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			minecraft.gui.setScreen(this);
			if (yes) {
				@Nullable String why = HqMenuBridge.resetAndRebuild(layout.id()); // runs on the next client tick
				if (why != null) {
					minecraft.gui.setScreen(new AlertScreen(() -> minecraft.gui.setScreen(this),
							Component.translatable("t3craft.demo.reset.title"), Component.literal(why)));
				}
			}
		}, title.copy().withStyle(ChatFormatting.YELLOW), body));
	}

	// ---- render -----------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		g.centeredText(font, title, width / 2, 8, TEXT);
		g.text(font, Component.translatable("t3craft.hqmenu.layout").withStyle(ChatFormatting.GRAY), list.x(), list.top() - font.lineHeight - 3, TEXT);

		g.fill(list.x(), list.top(), list.x() + list.width(), list.bottom(), WELL);
		g.enableScissor(list.x(), list.top(), list.x() + list.width(), list.bottom());
		int hover = list.rowAt(mouseX, mouseY);
		for (int r = 0; r < rowCount(); r++) {
			if (!list.visible(r)) {
				continue;
			}
			row(g, r, list.rowY(r), r == hover);
		}
		g.disableScissor();
		int[] thumb = list.thumb();
		if (thumb != null) {
			int sx = list.x() + list.width() - 3;
			g.fill(sx, thumb[0], sx + 2, thumb[0] + thumb[1], MUTED);
		}

		g.text(font, ChatDraw.clip(font, info().getString(), list.width()), list.x(), infoY, MUTED);
		super.extractRenderState(g, mouseX, mouseY, a); // widgets last: on a very short window they win over the list
	}

	// ---- list rows: one per layout, the primary one first -------------------------------------

	/** Rows of the list: one per layout. */
	private int rowCount() {
		return model.size();
	}

	private void ensureSelectedVisible() {
		list.ensureVisible(model.selectedIndex());
	}

	private void row(GuiGraphicsExtractor g, int i, int y, boolean hovered) {
		HqLayoutInfo l = model.get(i);
		int x0 = list.x();
		int x1 = list.x() + list.width() - (list.scrollable() ? 5 : 0);
		boolean sel = model.isSelected(i);
		if (sel) {
			g.fill(x0, y, x1, y + ROW_H, ROW_SELECTED);
			g.outline(x0, y, x1 - x0, ROW_H, BORDER_SELECTED);
		} else {
			if (hovered) {
				g.fill(x0, y, x1, y + ROW_H, ROW_HOVER);
			}
			g.fill(x0 + 2, y + ROW_H - 1, x1 - 2, y + ROW_H, BORDER);
		}
		int tx = x0 + 4;
		int avail = x1 - tx - 4;

		// tags from the right: [built] [main] [very large]
		int tagsW = 0;
		String builtTag = model.isBuilt(i) ? Component.translatable("t3craft.hqmenu.tag.built").getString() : null;
		String primaryTag = l.primary() ? Component.translatable("t3craft.hqmenu.tag.primary").getString() : null;
		String largeTag = model.warnLarge(i) ? Component.translatable("t3craft.hqmenu.tag.large").getString() : null;
		int right = x1 - 4;
		int tagY = y + 3;
		for (String tag : new String[] {builtTag, primaryTag, largeTag}) {
			if (tag == null) {
				continue;
			}
			int tw = font.width(tag);
			if (tagsW + tw + 6 > avail / 2) {
				continue; // narrow window: keep at least half the row for the name (the line below repeats the warning)
			}
			right -= tw;
			g.text(font, tag, right, tagY, tag.equals(builtTag) ? BUILT : tag.equals(primaryTag) ? PRIMARY : LARGE);
			right -= 6;
			tagsW += tw + 6;
		}
		String mark = sel ? "▶ " : "  ";
		String name = mark + Component.translatable(l.nameKey()).getString();
		g.text(font, ChatDraw.clip(font, name, avail - tagsW), tx, y + 3, sel ? TEXT : 0xFFDDDDDD);
		String desc = Component.translatable(l.descKey()).getString();
		g.text(font, ChatDraw.clip(font, desc, avail), tx + font.width("  "), y + 3 + font.lineHeight + 2, model.warnLarge(i) ? LARGE : MUTED);
	}

	private Component info() {
		if (packStatus != null) {
			return Component.literal(packStatus);
		}
		HqLayoutInfo built = model.built();
		if (!model.hqExists()) {
			return Component.translatable("t3craft.hqmenu.info.no_world", Component.translatable(model.selected().nameKey()));
		}
		if (model.builtId() == null) {
			return Component.translatable("t3craft.hqmenu.info.not_built", Component.translatable(model.selected().nameKey()));
		}
		Component builtName = built != null ? Component.translatable(built.nameKey()) : Component.literal(model.builtId());
		return Component.translatable("t3craft.hqmenu.info.built", builtName);
	}

	// ---- input ------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (super.mouseClicked(e, doubleClick)) {
			return true;
		}
		int r = list.rowAt(e.x(), e.y());
		if (r < 0) {
			return false;
		}
		if (r >= model.size() || !HqMenuKeys.choosesRow(e.button())) {
			return true; // not a left click (26.x: left = 1): only a left click on a layout chooses
		}
		choose(r);
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (list.contains(x, y) && list.wheel(dy)) {
			return true;
		}
		return super.mouseScrolled(x, y, dx, dy);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		HqMenuKeys.Action act = HqMenuKeys.action(e.key());
		if (act == HqMenuKeys.Action.OPEN) {
			if (getFocused() instanceof Button) {
				return super.keyPressed(e); // a focused button presses itself on Enter
			}
			demo.open(minecraft, this, false);
			return true;
		}
		if (act != HqMenuKeys.Action.NONE) {
			int d = HqMenuKeys.delta(act, model.selectedIndex(), model.size(), list.pageRows());
			choose(Math.max(0, Math.min(model.size() - 1, model.selectedIndex() + d)));
			return true; // arrows choose a layout here instead of walking the button focus
		}
		return super.keyPressed(e);
	}

	private void choose(int i) {
		packStatus = null;
		model.select(i);
		ensureSelectedVisible();
		refreshButtons();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
