package dev.t3craft.client.config;

import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.config.EnvironmentActions.EnvRow;
import dev.t3craft.client.config.T3CraftSettings.EnvPrefs;
import dev.t3craft.client.screen.DialogLayout;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3List;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.t3.Pairing;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

/**
 * Paired T3 environments as a page of the UI kit (docs/features/ui-kit.md): a {@link T3List} with the
 * status, show/hide, edit (name, colour, filter), unpair, control ("Enable control" upgrades a read-only
 * pairing / "Revoke" goes back to read-only) and, in the header, "Pair new..." (read + control by default:
 * paste the link, or the code plus the T3 URL). Shows ids, labels, URLs and connection status only: never a
 * token. Lives inside {@code T3Screen} (☁ in the sidebar) and as {@link EnvironmentsScreen}.
 */
public final class EnvironmentsPanel extends T3Panel {
	/** Narrower text columns than this put a row's buttons on a line of their own. */
	private static final int MIN_TEXT_W = 90;
	/** Show/Hide (eye), Edit (pencil), Unpair (trash; 8 px wider for the icon), control. */
	private static final int[] BUTTON_W = {50, 50, 58, 96};
	private static final int GAP = 4;
	/** Total width of a row's buttons. */
	private static final int BUTTONS_W = BUTTON_W[0] + BUTTON_W[1] + BUTTON_W[2] + BUTTON_W[3] + 3 * GAP;

	private final EnvironmentActions actions;
	private final T3List<EnvRow> list;
	/** The buttons of each row, created when the page is arranged. */
	private final Map<String, List<T3Button>> buttons = new HashMap<>();
	private DialogLayout.EnvRow rowLayout = DialogLayout.EnvRow.of(400, BUTTONS_W, MIN_TEXT_W);
	private String status = "";
	private Tone statusTone = Tone.MUTED;

	public EnvironmentsPanel(EnvironmentActions actions) {
		this.actions = actions;
		this.list = new T3List<>(DialogLayout.EnvRow.ROW, new T3List.Row<EnvRow>() {
			@Override
			public void render(GuiGraphicsExtractor g, Font font, ChatTheme t, EnvRow r, Rect rr, int mx, int my) {
				drawRow(g, font, t, r, rr, mx, my);
			}

			@Override
			public @Nullable List<String> tooltip(EnvRow r, Rect rr, double mx, double my) {
				for (T3Button b : buttons.getOrDefault(r.id(), List.of())) {
					List<String> tip = b.tooltipAt(mx, my);
					if (tip != null) {
						return tip;
					}
				}
				return null;
			}

			@Override
			public boolean mouseClicked(EnvRow r, Rect rr, double mx, double my, int button) {
				for (T3Button b : buttons.getOrDefault(r.id(), List.of())) {
					if (b.mouseClicked(mx, my, button)) {
						return true;
					}
				}
				return false;
			}
		});
	}

	@Override
	public String title() {
		return "T3 environments";
	}

	@Override
	protected int headerHeight() {
		return T3Control.H + 6;
	}

	@Override
	protected int footerHeight() {
		return 14;
	}

	@Override
	protected void arrangeHeader(Rect header) {
		pin(new T3Button(T3Icon.PLUS, "Pair new...", T3Button.Style.PRIMARY, () -> host().openScreen(actions.pairScreen(host().screen()))), header.x(),
				header.y(), 90, T3Control.H);
		pin(new T3Button(T3Icon.REFRESH_CW, "Refresh", () -> host().relayout()), header.x() + 94, header.y(), 70, T3Control.H);
	}

	@Override
	protected void arrangeFooter(Rect footer) {
		pinText(footer.x() + 2, footer.y() + 4, T3Draw.wrap(font(), status, footer.w() - 4, 1).getFirst(), statusTone);
	}

	@Override
	protected int arrange(Rect body, int y0) {
		List<EnvRow> rows = actions.list();
		rowLayout = DialogLayout.EnvRow.of(body.w() - 14, BUTTONS_W, MIN_TEXT_W);
		list.setRowHeight(rowLayout.rowH());
		list.setItems(rows);
		list.setEmptyText("No T3 environment paired yet. Use \"Pair new...\".\n\nOn the T3 host: " + Pairing.pairingHostCommand(false, null)
				+ "\n\n(read-only: " + Pairing.pairingHostCommand(true, null) + ")");
		buttons.clear();
		T3CraftSettings s = ConfigManager.get().settings();
		for (EnvRow r : rows) {
			buttons.put(r.id(), rowButtons(r, s.env(r.id())));
		}
		place(list, body.x(), y0, body.w(), body.h());
		return y0 + body.h(); // the list scrolls inside itself: the page does not
	}

	private List<T3Button> rowButtons(EnvRow r, EnvPrefs p) {
		T3Button visibility = new T3Button(p.visible() ? T3Icon.EYE_OFF : T3Icon.EYE, p.visible() ? "Hide" : "Show", () -> {
			ConfigManager.SaveResult res = ConfigManager.get().update(st -> st.withEnv(r.id(), st.env(r.id()).withVisible(!st.env(r.id()).visible())));
			if (res.ok()) {
				actions.reload();
				say((ConfigManager.get().settings().env(r.id()).visible() ? "Showing " : "Hiding ") + r.label(), Tone.OK);
			} else {
				say(res.message(), Tone.ERROR);
			}
			host().relayout();
		});
		T3Button edit = new T3Button(T3Icon.PENCIL, "Edit", () -> host().show(new EnvironmentEditPanel(actions, r)));
		T3Button unpair = new T3Button(T3Icon.TRASH, "Unpair", () -> confirm("Unpair " + r.label() + "?",
				List.of("Deletes the local session(s) for " + r.url() + " (read and control)"
						+ ". Revoke it on the host too: t3 auth session list / t3 auth session revoke <id>."),
				null, "Unpair", true, () -> {
					say(actions.unpair(r.id()), Tone.OK);
					host().relayout();
				}));
		boolean operate = actions.operateEnabled(r.id());
		T3Button control = new T3Button(operate ? T3Draw.tr("t3craft.operate.revoke.button") : T3Draw.tr("t3craft.operate.enable.short"), () -> {
			if (operate) {
				confirm(T3Draw.tr("t3craft.operate.revoke.title", r.label()), List.of(T3Draw.tr("t3craft.operate.revoke.body")), null,
						T3Draw.tr("t3craft.operate.revoke.button"), true, () -> {
							say(actions.revokeOperate(r.id()), Tone.OK);
							host().relayout();
						});
			} else {
				Screen next = actions.operateScreen(host().screen(), r.id());
				if (next != null) {
					host().openScreen(next);
				}
			}
		});
		control.setTooltip(T3Draw.tr(operate ? "t3craft.operate.revoke.tooltip" : "t3craft.operate.enable.tooltip"));
		return List.of(visibility, edit, unpair, control);
	}

	private void say(String msg, Tone tone) {
		status = msg;
		statusTone = tone;
	}

	private void drawRow(GuiGraphicsExtractor g, Font font, ChatTheme t, EnvRow r, Rect rr, int mx, int my) {
		T3CraftSettings s = ConfigManager.get().settings();
		EnvPrefs p = s.env(r.id());
		int textW = rowLayout.textW();
		int swatch = p.color() == null ? t.disabled() : 0xFF000000 | p.color();
		g.fill(rr.x() + 2, rr.y() + 4, rr.x() + 8, rr.y() + 20, swatch);
		String name = p.labelOr(r.label()) + (p.visible() ? "" : "  (hidden)") + (actions.operateEnabled(r.id()) ? "  [control]" : "");
		g.text(font, ChatDraw.clip(font, name, textW), rr.x() + 12, rr.y() + 3, p.visible() ? t.text() : t.disabled(), t.shadow());
		int sc = switch (r.status()) {
			case "ok" -> t.result();
			case "connecting" -> t.youText();
			case "-" -> t.muted();
			default -> t.errorText();
		};
		String line = r.status() + " · " + r.id() + " · " + r.url();
		g.text(font, ChatDraw.clip(font, line, textW), rr.x() + 12, rr.y() + 13, sc, t.shadow());
		int bx = rr.x() + rowLayout.buttonsX();
		int by = rr.y() + rowLayout.buttonsY() + (rowLayout.stacked() ? 0 : 4);
		List<T3Button> bs = buttons.getOrDefault(r.id(), List.of());
		for (int i = 0; i < bs.size(); i++) {
			bs.get(i).setBounds(new Rect(bx, by, BUTTON_W[i], T3Control.H));
			bs.get(i).render(g, font, t, mx, my);
			bx += BUTTON_W[i] + GAP;
		}
	}
}
