package dev.t3craft.client.debug;

import dev.t3craft.client.t3ui.kit.T3Button;
import dev.t3craft.client.t3ui.kit.T3Check;
import dev.t3craft.client.t3ui.kit.T3Dialog;
import dev.t3craft.client.t3ui.kit.T3DialogScreen;
import dev.t3craft.client.t3ui.kit.T3Draw.Tone;
import dev.t3craft.client.t3ui.kit.T3Field;
import dev.t3craft.client.t3ui.kit.T3Select;
import dev.t3craft.client.t3ui.kit.T3TextArea;
import dev.t3craft.client.t3ui.kit.T3Toggle;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Gallery dialog for the kit's form controls (issue #22; {@code /t3dev ui kit-dialog}): a text field, a text
 * area, a check box, a switch and a select in one {@link T3Dialog}, to try Tab / Shift+Tab, the focus ring
 * and Enter / Space on a screen of its own. Save only shows a line; nothing is stored.
 */
public final class UiKitDemoDialog extends T3DialogScreen {
	private static final List<String> VISIBILITY = List.of("Private", "Team", "Public");

	private @Nullable T3Field name;
	private @Nullable T3TextArea notes;
	private boolean includeNotes = true;
	private boolean pinned;
	private int visibility;
	private String message = "";

	public UiKitDemoDialog(@Nullable Screen parent) {
		super(Component.literal("Kit dialog"), parent);
	}

	@Override
	protected T3Dialog buildDialog() {
		if (name == null) { // they live as long as the screen: a rebuild keeps what was typed
			name = new T3Field(font, "Name").maxLength(40).hint("Name");
			notes = new T3TextArea(font, 4).maxLength(500).hint("Notes: Enter is a new line, Ctrl+Enter saves").onSubmit(this::save);
		}
		return new T3Dialog("Kit dialog: form controls", 380)
				.text("Tab / Shift+Tab move the focus, Enter or Space press the focused control, Esc closes.", Tone.MUTED)
				.gap(2)
				.field("Name", name)
				.area("Notes", notes)
				.control(new T3Check("Include the notes", includeNotes, v -> includeNotes = v))
				.control(new T3Toggle("Pinned", pinned, v -> pinned = v))
				.labelled("Visibility", new T3Select<String>(VISIBILITY, s -> s, VISIBILITY.get(visibility), v -> visibility = VISIBILITY.indexOf(v)))
				.gap(2)
				.buttons(new T3Button("Save", T3Button.Style.PRIMARY, this::save), new T3Button("Cancel", this::onClose))
				.text(() -> message, () -> Tone.OK, 2)
				.initialFocus(name)
				.defaultAction(this::save);
	}

	private void save() {
		message = "Saved \"" + name.value() + "\" (" + VISIBILITY.get(visibility) + (pinned ? ", pinned" : "") + ")"
				+ (includeNotes ? " with " + notes.value().length() + " characters of notes" : "");
	}
}
