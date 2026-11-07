package dev.t3craft.client.config;

import dev.t3craft.client.config.EnvironmentActions.EnvRow;
import dev.t3craft.client.config.T3CraftSettings.EnvPrefs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * One environment: rename, accent colour, show/hide, and its visibility filter (needs you /
 * working / recent toggles, recent window and cap; blank = global default). Written on "Save".
 */
public final class EnvironmentEditScreen extends BaseConfigScreen {
	/** Accent colours offered (Minecraft's chat palette); null = none. */
	static final List<Integer> PALETTE = List.of(0x55FFFF, 0x55FF55, 0xFFFF55, 0xFFAA00, 0xFF5555, 0xFF55FF, 0x5555FF, 0xFFFFFF, 0xAAAAAA);

	private final EnvironmentActions actions;
	private final EnvRow row;
	private final String originalName;
	private EnvPrefs draft;
	private String nameText;
	private String windowText;
	private String maxText;
	private @Nullable EditBox nameBox;
	private @Nullable EditBox windowBox;
	private @Nullable EditBox maxBox;

	public EnvironmentEditScreen(Screen parent, EnvironmentActions actions, EnvRow row) {
		super(Component.literal("Environment " + row.id()), parent);
		this.actions = actions;
		this.row = row;
		this.draft = ConfigManager.get().settings().env(row.id());
		this.originalName = draft.labelOr(row.label());
		this.nameText = originalName;
		this.windowText = draft.windowHours() == null ? "" : formatNumber(draft.windowHours());
		this.maxText = draft.max() == null ? "" : Integer.toString(draft.max());
	}

	/** Palette plus the current colour when it is a custom one from the file. */
	private List<@Nullable Integer> colours() {
		List<@Nullable Integer> c = new ArrayList<>();
		c.add(null);
		if (draft.color() != null && !PALETTE.contains(draft.color())) {
			c.add(draft.color());
		}
		c.addAll(PALETTE);
		return c;
	}

	private Component colourText() {
		Integer c = draft.color();
		return c == null
				? Component.literal("Colour: none")
				: Component.literal("Colour: ").append(Component.literal("■ " + ConfigCodec.formatColor(c)).withColor(c));
	}

	@Override
	protected void init() {
		int w = Math.min(320, width - 40);
		int x = width / 2 - w / 2;
		int half = w / 2 - 3;
		int y = 44;

		nameBox = new EditBox(font, x, y, w, 18, nameBox, Component.literal("Name"));
		nameBox.setMaxLength(32);
		nameBox.setValue(nameText);
		nameBox.setResponder(v -> nameText = v);
		addRenderableWidget(nameBox);

		addRenderableWidget(Button.builder(colourText(), b -> {
			List<@Nullable Integer> c = colours();
			int i = c.indexOf(draft.color());
			draft = draft.withColor(c.get((i + 1) % c.size()));
			b.setMessage(colourText());
		}).bounds(x, y + 26, half, 20).build());
		toggle("Visible", draft.visible(), x + half + 6, y + 26, half, v -> draft = draft.withVisible(v));

		int third = (w - 12) / 3;
		toggle("Needs you", draft.showNeedsYou(), x, y + 64, third,
				v -> draft = draft.withFilter(v, draft.showWorking(), draft.showRecent(), draft.windowHours(), draft.max()));
		toggle("Working", draft.showWorking(), x + third + 6, y + 64, third,
				v -> draft = draft.withFilter(draft.showNeedsYou(), v, draft.showRecent(), draft.windowHours(), draft.max()));
		toggle("Recent", draft.showRecent(), x + 2 * (third + 6), y + 64, third,
				v -> draft = draft.withFilter(draft.showNeedsYou(), draft.showWorking(), v, draft.windowHours(), draft.max()));

		windowBox = new EditBox(font, x, y + 100, half, 18, windowBox, Component.literal("Recent window (hours)"));
		windowBox.setMaxLength(8);
		windowBox.setValue(windowText);
		windowBox.setResponder(v -> windowText = v);
		windowBox.setHint(Component.literal("global: " + formatNumber(ConfigManager.get().settings().recentWindowHours())).withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(windowBox);
		maxBox = new EditBox(font, x + half + 6, y + 100, half, 18, maxBox, Component.literal("Max NPCs"));
		maxBox.setMaxLength(4);
		maxBox.setValue(maxText);
		maxBox.setResponder(v -> maxText = v);
		maxBox.setHint(Component.literal("global: " + ConfigManager.get().settings().maxPerEnvironment()).withStyle(ChatFormatting.DARK_GRAY));
		addRenderableWidget(maxBox);

		int by = height - 52;
		int bw = (w - 8) / 3;
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(x, by + 4, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), b -> {
			draft = EnvPrefs.DEFAULT.withLabel(draft.label());
			windowText = "";
			maxText = "";
			rebuildWidgets();
		}).bounds(x + bw + 4, by + 4, bw, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).bounds(x + 2 * (bw + 4), by + 4, bw, 20).build());
	}

	private void save() {
		Double hours = optionalNumber(windowText);
		Double max = optionalNumber(maxText);
		if (hours != null && !ConfigCodec.validWindow(hours)) {
			error("Recent window: hours 0 to " + (long) T3CraftSettings.MAX_WINDOW_HOURS + ", or blank for the global value");
			return;
		}
		if (max != null && !ConfigCodec.validMax(max)) {
			error("Max NPCs: whole number 1 to " + T3CraftSettings.MAX_PER_ENV_LIMIT + ", or blank for the global value");
			return;
		}
		String name = ConfigCodec.sanitizeLabel(nameText);
		if (name == null) {
			error("The name cannot be empty");
			return;
		}
		EnvPrefs next = draft.withFilter(draft.showNeedsYou(), draft.showWorking(), draft.showRecent(), hours,
				max == null ? null : max.intValue());
		ConfigManager.SaveResult r = ConfigManager.get().update(s -> s.withEnv(row.id(), next));
		if (!r.ok()) {
			error(r.message());
			return;
		}
		if (!name.equals(originalName)) {
			ok(actions.rename(row.id(), name)); // rename reloads the sources itself
		} else {
			actions.reload();
			ok(r.message() + " and reloaded");
		}
		draft = ConfigManager.get().settings().env(row.id());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int w = Math.min(320, width - 40);
		int x = width / 2 - w / 2;
		int y = 44;
		g.centeredText(font, Component.literal(row.url() + " · " + row.status()).withStyle(ChatFormatting.GRAY), width / 2, 24, 0xFFFFFFFF);
		g.text(font, "Name (shown on the NPCs)", x, y - 10, 0xFFA0A0A0);
		g.text(font, "Show threads that are…", x, y + 54, 0xFFA0A0A0);
		g.text(font, "Recent window (hours)", x, y + 90, 0xFFA0A0A0);
		g.text(font, "Max NPCs", x + w / 2 + 3, y + 90, 0xFFA0A0A0);
	}
}
