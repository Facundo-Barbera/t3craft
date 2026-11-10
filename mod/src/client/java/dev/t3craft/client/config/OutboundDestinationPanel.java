package dev.t3craft.client.config;

import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.outbound.OutboundClient;
import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Control;
import dev.t3craft.client.t3ui.kit.T3Draw;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Icon;
import dev.t3craft.client.t3ui.kit.T3Panel;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import dev.t3craft.outbound.Destination;
import dev.t3craft.outbound.EventType;
import dev.t3craft.outbound.OutboundStore;
import dev.t3craft.outbound.Template;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * One outbound destination as a page of the UI kit: name, template, enabled, the webhook URL (a secret:
 * the field starts empty, whatever is typed replaces the stored URL on Save, and it is never shown again;
 * the page only says whether one is stored), tap action for push, the "pending only" choice and the event
 * filter. Written on "Save"; "Delete" asks first because it also removes the stored URL.
 */
public final class OutboundDestinationPanel extends T3Panel {
	private static final int ROW = 18;
	private static final int MAX_W = 440;

	private final OutboundStore store;
	private final String id;
	/** Told after a save or a delete, so the list page reads the file again. */
	private final Runnable onChanged;
	private Destination d;
	private boolean urlSet;
	private boolean closed;
	private @Nullable T3Field nameField;
	private @Nullable T3Field urlField;
	private @Nullable T3Field clickField;
	private String status = "";
	private Tone statusTone = Tone.MUTED;

	public OutboundDestinationPanel(OutboundStore store, String id, Runnable onChanged) {
		this.store = store;
		this.id = id;
		this.onChanged = onChanged;
		Destination found = store.load().destination(id);
		this.d = found != null ? found : Destination.create(id, id);
		this.urlSet = store.hasUrl(id);
	}

	@Override
	public String title() {
		return "Outbound destination";
	}

	@Override
	public void closed() {
		closed = true;
	}

	private void say(String msg, Tone tone) {
		status = msg;
		statusTone = tone;
	}

	// ---- layout ----------------------------------------------------------------------------------------

	@Override
	protected int footerHeight() {
		return T3Control.H + 10;
	}

	@Override
	protected void arrangeFooter(Rect footer) {
		int y = footer.y() + 5;
		int bw = 56;
		pin(new T3Button(T3Icon.CHEVRON_LEFT, "Back", () -> host().back()), footer.right() - bw, y, bw, T3Control.H);
		pin(new T3Button("Save", T3Button.Style.PRIMARY, this::save), footer.right() - 2 * bw - 4, y, bw, T3Control.H);
		List<String> lines = T3Draw.wrap(font(), status, footer.w() - 2 * bw - 14, 2);
		for (int i = 0; i < lines.size(); i++) {
			pinText(footer.x() + 2, footer.y() + 4 + i * T3Draw.lineHeight(font()), lines.get(i), statusTone);
		}
	}

	@Override
	protected int arrange(Rect body, int y0) {
		int w = Math.min(MAX_W, body.w() - 8);
		int x = body.x() + (body.w() - w) / 2 - 3;
		int y = y0 + 4;
		y = notice(x + 4, y, w - 8, "Everything this destination receives leaves your computer.", Tone.WARN) + 6;

		y = text(x + 4, y, w - 8, "Name", Tone.MUTED);
		placeField(nameField(), x, y + 1, w);
		y += T3Control.H + 7;

		y = text(x + 4, y, w - 8, "Template", Tone.MUTED);
		T3Select<Template> template = new T3Select<>(List.of(Template.values()), t -> t.label, d.template(), t -> {
			d = d.withTemplate(t);
			host().relayout(); // the tap action only exists for push
		});
		place(template, x, y + 1, w, T3Control.H);
		y += T3Control.H + 7;

		y = toggle(x, y, w, "Enabled", d.enabled(), v -> d = d.withEnabled(v));
		y += 6;

		y = text(x + 4, y, w - 8, "Webhook URL (secret)", Tone.MUTED);
		placeField(urlField().hint(urlSet ? "Stored. Paste a new URL to replace it" : "Paste the webhook URL here"), x, y + 1, w);
		y += T3Control.H + 3;
		y = notice(x + 4, y, w - 8, urlSet ? "A URL is stored. It is never shown again." : "No URL stored yet: nothing is sent until you save one.",
				urlSet ? Tone.OK : Tone.WARN) + 3;
		T3Button test = new T3Button(T3Icon.SEND, "Send test", this::test);
		test.setTooltip("Sends a test message to the stored URL.", "Save first if you just pasted a new one.");
		place(test, x, y, w, T3Control.H);
		y += ROW + 6;

		if (d.template() == Template.PUSH) {
			y = text(x + 4, y, w - 8, "Tap action URL (optional)", Tone.MUTED);
			placeField(clickField(), x, y + 1, w);
			y += T3Control.H + 3;
			y = notice(x + 4, y, w - 8, "{environment} and {thread} are filled in.", Tone.MUTED) + 6;
		}

		y = toggle(x, y, w, "Pending only", d.pendingOnly(), v -> {
			d = d.withPendingOnly(v);
			host().relayout(); // the note under it follows
		});
		y = notice(x + 4, y + 2, w - 8, d.pendingOnly()
				? "Pending only: no titles, projects or text are sent, just 'something is pending'."
				: "Thread titles, projects and environment ids are sent.", d.pendingOnly() ? Tone.OK : Tone.WARN) + 8;

		y = text(x + 4, y, w - 8, "Send when", Tone.TITLE) + 3;
		for (EventType type : EventType.values()) {
			y = toggle(x, y, w, type.label, d.events().contains(type), v -> d = d.withEvent(type, v));
		}
		y += 8;

		T3Button delete = new T3Button(T3Icon.TRASH, "Delete destination", T3Button.Style.DANGER, this::askDelete);
		delete.setTooltip("Removes this destination and its stored URL.");
		place(delete, x, y, w, T3Control.H);
		return y + ROW + 6;
	}

	private int toggle(int x, int y, int w, String label, boolean value, Consumer<Boolean> set) {
		place(new T3Toggle(label, value, set), x, y, w, T3Control.H);
		return y + ROW;
	}

	// ---- state -----------------------------------------------------------------------------------------------

	// The fields live as long as the page: a relayout keeps what was typed.

	private T3Field nameField() {
		if (nameField == null) {
			nameField = new T3Field(font(), "Name").maxLength(Destination.MAX_NAME).hint("Name");
			nameField.setValue(d.name());
		}
		return nameField;
	}

	/** Always starts empty: the stored URL is never read back into the page. */
	private T3Field urlField() {
		if (urlField == null) {
			urlField = new T3Field(font(), "Webhook URL").maxLength(OutboundStore.MAX_URL_LENGTH);
		}
		return urlField;
	}

	private T3Field clickField() {
		if (clickField == null) {
			clickField = new T3Field(font(), "Tap action").maxLength(Destination.MAX_CLICK).hint("https://... (optional)");
			clickField.setValue(d.clickUrl() == null ? "" : d.clickUrl());
		}
		return clickField;
	}

	// ---- actions ---------------------------------------------------------------------------------------------

	private void save() {
		String url = urlField().value().strip();
		if (!url.isEmpty()) {
			String why = OutboundStore.validate(url);
			if (why != null) {
				fail(why);
				return;
			}
		}
		Destination next = d.withName(nameField().value()).withClickUrl(clickField == null ? d.clickUrl() : clickField.value());
		try {
			store.save(store.load().withDestination(next));
			if (!url.isEmpty()) {
				store.setUrl(id, url);
				urlSet = true;
				urlField().setValue("");
			}
		} catch (IOException | IllegalArgumentException e) {
			fail("Could not save: " + e.getClass().getSimpleName());
			return;
		}
		d = next;
		nameField().setValue(next.name());
		say("Saved", Tone.OK);
		onChanged.run();
		host().relayout();
	}

	private void test() {
		if (!urlField().value().isBlank()) {
			say("Save first, then send the test", Tone.WARN);
			host().relayout();
			return;
		}
		say("Sending test...", Tone.MUTED);
		host().relayout();
		OutboundClient.sendTest(id, msg -> Minecraft.getInstance().execute(() -> {
			if (!closed) {
				say("Test: " + msg, Tone.NORMAL);
				host().relayout();
			}
		}));
	}

	private void askDelete() {
		confirm("Delete " + d.name() + "?", List.of("Removes this destination and the webhook URL stored for it."), null, "Delete", true, () -> {
			try {
				store.save(store.load().withoutDestination(id));
				store.deleteUrl(id);
			} catch (IOException e) {
				fail("Could not delete: " + e.getClass().getSimpleName());
				return;
			}
			onChanged.run();
			host().back();
		});
	}

	private void fail(String msg) {
		say(msg, Tone.ERROR);
		host().relayout();
	}
}
