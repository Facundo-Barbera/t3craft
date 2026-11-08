package dev.t3craft.client.t3ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.MouseButtons;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.compose.ComposerSkin;
import dev.t3craft.client.chat.compose.ComposerText;
import dev.t3craft.client.chat.compose.ComposerView;
import dev.t3craft.client.t3ui.actions.ActionsClient;
import dev.t3craft.client.t3ui.actions.NewThreadForm;
import dev.t3craft.client.t3ui.actions.ProjectsService;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The two chips a {@link ThreadDraft} adds to the composer bar, each with a popover that opens
 * upwards (T3's composer menus open on the top side):
 * <ul>
 *   <li><b>project</b> ({@code DraftHeroProjectHeading} / the project picker): "▣ project", or
 *   "env · project" when several environments are paired; the popover lists every project of every
 *   connected environment ({@link ProjectsService}).</li>
 *   <li><b>workspace</b> ({@code BranchToolbarEnvModeSelector} + {@code WorktreeBaseBranchPicker}):
 *   "Local" or "New worktree"; for a worktree the popover also has the base branch, an optional
 *   branch name and "start from origin".</li>
 * </ul>
 * The branch fields are small {@link ComposerText} editors: while the popover has one focused it
 * takes the keyboard ({@link #keyPressed}, {@link #charTyped}) and SDL text input.
 */
final class DraftControls {
	private static final int ROW = 13;

	private final Supplier<@Nullable ThreadDraft> draft;
	private final Consumer<ProjectsService.Project> onProject;
	private final Font font;
	final ProjectChip project = new ProjectChip();
	final WorkspaceChip workspace = new WorkspaceChip();

	DraftControls(Font font, Supplier<@Nullable ThreadDraft> draft, Consumer<ProjectsService.Project> onProject) {
		this.font = font;
		this.draft = draft;
		this.onProject = onProject;
	}

	List<ComposerView.Control> controls() {
		return List.of(project, workspace);
	}

	List<T3Slots.Popup> popups() {
		return List.of(project, workspace);
	}

	void closeAll() {
		project.open = false;
		workspace.close();
	}

	private record Row(Rect r, Runnable action) {
	}

	/** Shared popover frame: rows hit-tested, outside click closes. */
	private abstract class Popover implements ComposerView.Control, T3Slots.Popup {
		boolean open;
		int cx;
		int cy;
		int cw;
		Rect pop = Rect.EMPTY;
		final List<Row> rows = new ArrayList<>();

		@Override
		public boolean visible() {
			return draft.get() != null;
		}

		@Override
		public boolean isOpen() {
			return open;
		}

		void toggle() {
			boolean was = open;
			closeAll();
			open = !was;
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (!open) {
				// the chip itself (the composer routes clicks in its rect here)
				if (MouseButtons.isLeft(button)) {
					toggle();
				}
				return true;
			}
			for (Row r : List.copyOf(rows)) {
				if (r.r().contains(mx, my)) {
					if (MouseButtons.isLeft(button)) {
						r.action().run();
					}
					return true;
				}
			}
			if (pop.contains(mx, my)) {
				return true;
			}
			// outside: close (a click on the chip itself just closes it)
			close();
			return true;
		}

		void close() {
			open = false;
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			if (open && e.key() == InputConstants.KEY_ESCAPE) {
				close();
				return true;
			}
			return false;
		}

		@Override
		public boolean mouseScrolled(double mx, double my, double dy) {
			return open && pop.contains(mx, my);
		}

		/** Frame of {@code w}×{@code h} opening upwards from the chip, kept on screen. */
		Rect frame(GuiGraphicsExtractor g, int w, int h) {
			int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
			int x = Math.max(2, Math.min(cx, sw - w - 2));
			int y = Math.max(2, cy - h - 3);
			pop = new Rect(x, y, w, h);
			g.nextStratum(); // over the transcript's text, which is drawn in the stratum below
			ComposerSkin.box(g, x, y, w, h, ComposerSkin.POPUP, ComposerSkin.BORDER_FOCUS);
			rows.clear();
			return pop;
		}

		void header(GuiGraphicsExtractor g, String label, int x, int y) {
			g.text(font, label, x, y + 2, ComposerSkin.PLACEHOLDER, false);
		}

		/** A selectable row; returns the next y. */
		int item(GuiGraphicsExtractor g, String label, @Nullable String detail, boolean selected, int x, int y, int w, int mouseX, int mouseY,
				Runnable action) {
			Rect r = new Rect(x, y, w, ROW);
			boolean hover = r.contains(mouseX, mouseY);
			if (hover || selected) {
				ComposerSkin.round(g, r.x(), r.y(), r.w(), r.h(), hover ? ComposerSkin.HOVER : ComposerSkin.OPEN);
			}
			String check = selected ? "✓ " : "   ";
			int tx = r.x() + 4;
			g.text(font, check, tx, y + 3, ComposerSkin.TEXT, false);
			tx += font.width("✓ ") + 1;
			int room = r.right() - tx - 4;
			String d = detail == null ? "" : detail;
			int dw = d.isEmpty() ? 0 : Math.min(font.width(d), room / 2);
			g.text(font, ChatDraw.clip(font, label, room - dw - (dw > 0 ? 6 : 0)), tx, y + 3, ComposerSkin.TEXT, false);
			if (dw > 0) {
				g.text(font, ChatDraw.clip(font, d, dw), r.right() - 4 - dw, y + 3, ComposerSkin.PLACEHOLDER, false);
			}
			rows.add(new Row(r, action));
			return y + ROW;
		}
	}

	// ---- project ------------------------------------------------------------------------------

	final class ProjectChip extends Popover {
		private int scroll;

		private String label(ThreadDraft d) {
			boolean multi = ActionsClient.environments().size() > 1;
			return multi ? d.envLabel + " · " + d.projectLabel : d.projectLabel;
		}

		@Override
		public int width(Font f) {
			ThreadDraft d = draft.get();
			return d == null ? 0 : Math.min(140, ComposerSkin.chipWidth(f, "▣", label(d), ""));
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font f, int x, int y, int mouseX, int mouseY) {
			ThreadDraft d = draft.get();
			if (d == null) {
				return;
			}
			cx = x;
			cy = y;
			cw = width(f);
			boolean hover = mouseX >= x && mouseX < x + cw && mouseY >= y && mouseY < y + ComposerSkin.CHIP_H;
			String l = ChatDraw.clip(f, label(d), cw - ComposerSkin.chipWidth(f, "▣", "", ""));
			ComposerSkin.chip(g, f, x, y, "▣", 0xFF9AA7B8, l, ComposerSkin.TEXT, "", hover, open);
			if (hover && !open) {
				g.setTooltipForNextFrame(f, Component.literal("Project · " + d.envLabel + " / " + d.projectLabel), mouseX, mouseY);
			}
			if (open) {
				drawPopover(g, d, mouseX, mouseY);
			}
		}

		private void drawPopover(GuiGraphicsExtractor g, ThreadDraft d, int mouseX, int mouseY) {
			List<ProjectsService.Project> ps = ActionsClient.projects().all();
			int maxRows = 10;
			int shown = Math.max(1, Math.min(maxRows, ps.size()));
			int w = 170;
			for (ProjectsService.Project p : ps) {
				w = Math.max(w, Math.min(260, font.width("✓ " + p.label() + "  " + p.envLabel()) + 20));
			}
			int h = 4 + 12 + shown * ROW + 4;
			Rect fr = frame(g, w, h);
			int x = fr.x() + 3;
			int y = fr.y() + 3;
			header(g, "Project", x + 2, y);
			y += 12;
			if (ps.isEmpty()) {
				g.text(font, "No projects. ⋯ › Register project…", x + 4, y + 3, ComposerSkin.MUTED, false);
				return;
			}
			scroll = Math.max(0, Math.min(scroll, ps.size() - shown));
			boolean multi = ps.stream().map(ProjectsService.Project::envId).distinct().count() > 1;
			for (int i = scroll; i < ps.size() && i < scroll + shown; i++) {
				ProjectsService.Project p = ps.get(i);
				boolean sel = p.envId().equals(d.envId) && p.projectId().equals(d.projectId);
				y = item(g, p.label(), multi ? p.envLabel() : null, sel, x, y, fr.w() - 6, mouseX, mouseY, () -> {
					open = false;
					onProject.accept(p);
				});
			}
		}

		@Override
		public boolean mouseScrolled(double mx, double my, double dy) {
			if (open && pop.contains(mx, my)) {
				scroll += dy > 0 ? -1 : 1;
				return true;
			}
			return false;
		}
	}

	// ---- workspace ----------------------------------------------------------------------------

	final class WorkspaceChip extends Popover {
		private final ComposerText base = new ComposerText();
		private final ComposerText branch = new ComposerText();
		/** The focused field: base, branch or null. */
		private @Nullable ComposerText editing;
		private Rect baseRect = Rect.EMPTY;
		private Rect branchRect = Rect.EMPTY;

		@Override
		public int width(Font f) {
			ThreadDraft d = draft.get();
			return d == null ? 0 : ComposerSkin.chipWidth(f, icon(d), d.workspace.workspace.label, detail(d));
		}

		private static String icon(ThreadDraft d) {
			return d.workspace.workspace == NewThreadForm.Workspace.WORKTREE ? "⑂" : "▢";
		}

		private static String detail(ThreadDraft d) {
			return d.workspace.workspace == NewThreadForm.Workspace.WORKTREE && !d.workspace.baseRef.isBlank() ? "from " + d.workspace.baseRef.strip() : "";
		}

		@Override
		void toggle() {
			super.toggle();
			ThreadDraft d = draft.get();
			if (open && d != null) {
				base.set(d.workspace.baseRef);
				branch.set(d.workspace.branch);
			}
		}

		@Override
		void close() {
			commit();
			setEditing(null);
			open = false;
		}

		private void commit() {
			ThreadDraft d = draft.get();
			if (d != null && open) {
				d.workspace.baseRef = base.text().strip();
				d.workspace.branch = branch.text().strip();
			}
		}

		private void setEditing(@Nullable ComposerText f) {
			if (editing == f) {
				return;
			}
			var tim = Minecraft.getInstance().textInputManager();
			if (editing != null) {
				tim.onTextInputFocusChange(this, false);
			}
			editing = f;
			if (f != null) {
				tim.onTextInputFocusChange(this, true);
			}
		}

		@Override
		public void render(GuiGraphicsExtractor g, Font f, int x, int y, int mouseX, int mouseY) {
			ThreadDraft d = draft.get();
			if (d == null) {
				return;
			}
			cx = x;
			cy = y;
			cw = width(f);
			boolean hover = mouseX >= x && mouseX < x + cw && mouseY >= y && mouseY < y + ComposerSkin.CHIP_H;
			ComposerSkin.chip(g, f, x, y, icon(d), 0xFF9AA7B8, d.workspace.workspace.label, ComposerSkin.TEXT, detail(d), hover, open);
			if (hover && !open) {
				g.setTooltipForNextFrame(f, Component.literal(d.workspace.workspace == NewThreadForm.Workspace.WORKTREE
						? "Workspace · a new git worktree" : "Workspace · the project checkout"), mouseX, mouseY);
			}
			if (open) {
				drawPopover(g, d, mouseX, mouseY);
			}
		}

		private void drawPopover(GuiGraphicsExtractor g, ThreadDraft d, int mouseX, int mouseY) {
			boolean wt = d.workspace.workspace == NewThreadForm.Workspace.WORKTREE;
			int w = 200;
			int h = 4 + 12 + 2 * ROW + (wt ? 6 + 2 * (10 + 15) + ROW : 0) + 4;
			Rect fr = frame(g, w, h);
			int x = fr.x() + 3;
			int y = fr.y() + 3;
			int iw = fr.w() - 6;
			header(g, "Workspace", x + 2, y);
			y += 12;
			y = item(g, "Local", "project checkout", !wt, x, y, iw, mouseX, mouseY, () -> {
				d.workspace.workspace = NewThreadForm.Workspace.LOCAL;
				close();
			});
			y = item(g, "New worktree", null, wt, x, y, iw, mouseX, mouseY, () -> d.workspace.workspace = NewThreadForm.Workspace.WORKTREE);
			baseRect = Rect.EMPTY;
			branchRect = Rect.EMPTY;
			if (!wt) {
				setEditing(null);
				return;
			}
			y += 6;
			header(g, "Base branch", x + 2, y - 2);
			y += 10;
			baseRect = new Rect(x + 2, y, iw - 4, 13);
			field(g, base, baseRect, "main");
			y += 15;
			header(g, "Branch name (optional)", x + 2, y - 2);
			y += 10;
			branchRect = new Rect(x + 2, y, iw - 4, 13);
			field(g, branch, branchRect, "t3code/…");
			y += 15;
			boolean origin = d.workspace.startFromOrigin;
			item(g, "Start from origin", null, origin, x, y, iw, mouseX, mouseY, () -> d.workspace.startFromOrigin = !d.workspace.startFromOrigin);
			String bad = !base.text().isBlank() && !NewThreadForm.validRef(base.text().strip()) ? "not a valid branch"
					: !branch.text().isBlank() && !NewThreadForm.validRef(branch.text().strip()) ? "branch name not valid" : null;
			if (bad != null) {
				g.text(font, bad, fr.right() - 4 - font.width(bad), fr.y() + 5, ComposerSkin.ERROR, false);
			}
			if (editing != null) {
				Rect er = editing == base ? baseRect : branchRect;
				Minecraft.getInstance().textInputManager().setTextInputArea(er.x(), er.y(), er.right(), er.bottom());
			}
		}

		private void field(GuiGraphicsExtractor g, ComposerText t, Rect r, String placeholder) {
			boolean on = editing == t;
			ComposerSkin.box(g, r.x(), r.y(), r.w(), r.h(), 0xFF0F0F12, on ? ComposerSkin.BORDER_FOCUS : ComposerSkin.BORDER);
			t.layout(Integer.MAX_VALUE / 4, font::width);
			int tx = r.x() + 4;
			int room = r.w() - 8;
			String s = t.text();
			if (s.isEmpty()) {
				g.text(font, placeholder, tx, r.y() + 3, ComposerSkin.PLACEHOLDER, false);
			} else {
				// keep the caret in view: show the tail when the text is longer than the field
				int caretPx = font.width(s.substring(0, t.caret()));
				int shift = Math.max(0, caretPx - room + 2);
				g.enableScissor(tx, r.y(), tx + room, r.bottom());
				if (t.hasSelection()) {
					int a = tx - shift + font.width(s.substring(0, t.selectionStart()));
					int b = tx - shift + font.width(s.substring(0, t.selectionEnd()));
					g.fill(a, r.y() + 2, b, r.bottom() - 2, ComposerSkin.SELECTION);
				}
				g.text(font, s, tx - shift, r.y() + 3, ComposerSkin.TEXT, false);
				g.disableScissor();
				if (on && ChatDraw.caretOn()) {
					int cxp = tx - shift + caretPx;
					g.fill(cxp, r.y() + 2, cxp + 1, r.bottom() - 2, ComposerSkin.TEXT);
				}
				return;
			}
			if (on && ChatDraw.caretOn()) {
				g.fill(tx, r.y() + 2, tx + 1, r.bottom() - 2, ComposerSkin.TEXT);
			}
		}

		@Override
		public boolean mouseClicked(double mx, double my, int button) {
			if (open && MouseButtons.isLeft(button)) {
				if (baseRect.contains(mx, my)) {
					setEditing(base);
					base.setCaret(base.text().length(), false);
					return true;
				}
				if (branchRect.contains(mx, my)) {
					setEditing(branch);
					branch.setCaret(branch.text().length(), false);
					return true;
				}
				if (pop.contains(mx, my)) {
					setEditing(null);
				}
			}
			return super.mouseClicked(mx, my, button);
		}

		@Override
		public boolean keyPressed(KeyEvent e) {
			if (!open) {
				return false;
			}
			ComposerText f = editing;
			if (f == null) {
				return super.keyPressed(e);
			}
			if (e.key() == InputConstants.KEY_ESCAPE) {
				setEditing(null);
				return true;
			}
			if (e.key() == InputConstants.KEY_TAB) {
				setEditing(f == base ? branch : base);
				return true;
			}
			ComposerText.KeyResult r = f.key(e.key(), e.hasShiftDown(), e.hasControlDownWithQuirk(), new ComposerText.Clipboard() {
				@Override
				public String get() {
					return Minecraft.getInstance().keyboardHandler.getClipboard().replace('\n', ' ');
				}

				@Override
				public void set(String s) {
					Minecraft.getInstance().keyboardHandler.setClipboard(s);
				}
			});
			if (r == ComposerText.KeyResult.SUBMIT) {
				close();
			} else {
				commit();
			}
			return true;
		}

		@Override
		public boolean charTyped(int codepoint) {
			ComposerText f = editing;
			if (!open || f == null) {
				return false;
			}
			if (codepoint != ' ' && f.text().length() < 200) {
				f.typed(codepoint);
				commit();
			}
			return true;
		}
	}

	/** Routes a typed character to an open popover's field. */
	boolean charTyped(int codepoint) {
		return workspace.charTyped(codepoint);
	}
}
