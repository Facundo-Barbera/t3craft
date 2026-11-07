package dev.t3craft.client.config;

import dev.t3craft.client.config.T3CraftSettings.Notifications;
import dev.t3craft.client.demo.DemoClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * T3Craft settings: notification and sound toggles, the default visibility filter, the portable
 * terminal's held screen, links to the environment list and the key binds. Edits go to a draft and are written on "Save" (then the mod
 * reloads its sources, like {@code /t3craft reload}). Opened from Mod Menu, {@code /t3craft config}
 * or the J key. The bridge key is never shown, only whether one is set.
 */
public final class SettingsScreen extends BaseConfigScreen {
	private final EnvironmentActions actions;
	private T3CraftSettings draft;
	private @Nullable EditBox windowBox;
	private @Nullable EditBox maxBox;
	private String windowText;
	private String maxText;

	public SettingsScreen(@Nullable Screen parent, EnvironmentActions actions) {
		super(Component.literal("T3Craft settings"), parent);
		this.actions = actions;
		this.draft = ConfigManager.get().settings();
		this.windowText = formatNumber(draft.recentWindowHours());
		this.maxText = Integer.toString(draft.maxPerEnvironment());
		if (ConfigManager.get().readOnly()) {
			error("config/t3craft.json is from a newer T3Craft: changes will not be saved");
		} else if (!ConfigManager.get().warnings().isEmpty()) {
			info(ConfigManager.get().warnings().size() + " config warning(s) fixed with defaults (see log)");
		}
	}

	@Override
	protected void init() {
		int colW = Math.min(150, (width - 40) / 3);
		int gap = 6;
		int total = colW * 3 + gap * 2;
		int x0 = width / 2 - total / 2;
		int x1 = x0 + colW + gap;
		int x2 = x1 + colW + gap;
		int y = 44;
		Notifications n = draft.notifications();

		toggle("All notifications", n.enabled(), x0, y, colW, v -> setN(new Notifications(v, n().chat(), n().toast(), n().sound(),
				n().soundAttention(), n().soundDone(), n().soundError(), n().connectionToasts())));
		toggle("Chat lines", n.chat(), x0, y + 24, colW, v -> setN(new Notifications(n().enabled(), v, n().toast(), n().sound(),
				n().soundAttention(), n().soundDone(), n().soundError(), n().connectionToasts())));
		toggle("Toasts", n.toast(), x0, y + 48, colW, v -> setN(new Notifications(n().enabled(), n().chat(), v, n().sound(),
				n().soundAttention(), n().soundDone(), n().soundError(), n().connectionToasts())));
		toggle("Connection toasts", n.connectionToasts(), x0, y + 72, colW, v -> setN(new Notifications(n().enabled(), n().chat(),
				n().toast(), n().sound(), n().soundAttention(), n().soundDone(), n().soundError(), v)));

		toggle("Sounds", n.sound(), x1, y, colW, v -> setN(new Notifications(n().enabled(), n().chat(), n().toast(), v,
				n().soundAttention(), n().soundDone(), n().soundError(), n().connectionToasts())));
		toggle("Needs you", n.soundAttention(), x1, y + 24, colW, v -> setN(new Notifications(n().enabled(), n().chat(), n().toast(),
				n().sound(), v, n().soundDone(), n().soundError(), n().connectionToasts())));
		toggle("Done", n.soundDone(), x1, y + 48, colW, v -> setN(new Notifications(n().enabled(), n().chat(), n().toast(),
				n().sound(), n().soundAttention(), v, n().soundError(), n().connectionToasts())));
		toggle("Error", n.soundError(), x1, y + 72, colW, v -> setN(new Notifications(n().enabled(), n().chat(), n().toast(),
				n().sound(), n().soundAttention(), n().soundDone(), v, n().connectionToasts())));

		windowBox = new EditBox(font, x2, y + 10, colW, 18, windowBox, Component.literal("Recent window (hours)"));
		windowBox.setMaxLength(8);
		windowBox.setValue(windowText);
		windowBox.setResponder(v -> windowText = v);
		addRenderableWidget(windowBox);
		maxBox = new EditBox(font, x2, y + 44, colW, 18, maxBox, Component.literal("Max per environment"));
		maxBox.setMaxLength(4);
		maxBox.setValue(maxText);
		maxBox.setResponder(v -> maxText = v);
		addRenderableWidget(maxBox);
		toggle("Subagents", draft.includeSubagents(), x2, y + 72, colW,
				v -> draft = draft.withVisibility(draft.recentWindowHours(), draft.maxPerEnvironment(), v));

		// Portable T3 Terminal: live summary on the held item's screen (portable.heldScreen).
		toggle("Held terminal screen", draft.portable().heldScreen(), x0, y + 96, colW,
				v -> draft = draft.withPortable(draft.portable().withHeldScreen(v)));

		// HUD status bar (hud.*): on/off next to "Held terminal screen"; placement cycle and "hide
		// when idle" on the next row when the window is tall enough (else edit config/t3craft.json).
		toggle("HUD status bar", draft.hud().statusBar(), x2, y + 96, colW, v -> draft = draft.withHud(draft.hud().withStatusBar(v)));
		if (y + 144 <= height - 80) {
			addRenderableWidget(Button.builder(placementText(), b -> {
				draft = draft.withHud(draft.hud().nextPlacement());
				b.setMessage(placementText());
			}).bounds(x0, y + 120, colW, 20).build());
			toggle("Hide bar when idle", draft.hud().hideWhenIdle(), x2, y + 120, colW,
					v -> draft = draft.withHud(draft.hud().withHideWhenIdle(v)));
		}

		int by = height - 52;
		int bw = Math.min(120, (width - 40) / 3 - 4);
		int bx = width / 2 - (bw * 3 + 8) / 2;
		addRenderableWidget(Button.builder(Component.literal("Environments…"),
				b -> minecraft.gui.setScreen(new EnvironmentsScreen(this, actions))).bounds(bx, by - 24, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Key binds…"),
				b -> minecraft.gui.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(bx + bw + 4, by - 24, bw, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Reload from disk"), b -> {
			draft = ConfigManager.get().load();
			windowText = formatNumber(draft.recentWindowHours());
			maxText = Integer.toString(draft.maxPerEnvironment());
			actions.reload();
			ok("Reloaded " + ConfigManager.get().file().getFileName());
			rebuildWidgets();
		}).bounds(bx + 2 * (bw + 4), by - 24, bw, 20).build());

		// Testing aid: wipe and recreate the demo HQ world (dev.t3craft.client.demo.HqReset). Top right.
		int rw = Math.min(110, width / 4);
		addRenderableWidget(Button.builder(Component.translatable("t3craft.demo.reset.button").withStyle(ChatFormatting.RED), b -> resetHq())
				.bounds(width - rw - 6, 6, rw, 20)
				.tooltip(Tooltip.create(Component.translatable("t3craft.demo.reset.button.tooltip")))
				.build());

		int half = Math.min(150, (width - 40) / 2 - 4);
		addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(width / 2 - half - 4, by + 4, half, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 + 4, by + 4, half, 20).build());
	}

	private void resetHq() {
		String refusal = DemoClient.resetRefusal();
		if (refusal != null) {
			error(refusal);
			return;
		}
		minecraft.gui.setScreen(new ConfirmScreen(yes -> {
			if (yes) {
				DemoClient.requestReset();
				minecraft.gui.setScreen(null);
			} else {
				minecraft.gui.setScreen(this);
			}
		}, Component.translatable("t3craft.demo.reset.title"), Component.translatable("t3craft.demo.reset.body")));
	}

	private Component placementText() {
		return Component.literal("Bar: " + draft.hud().position() + " " + draft.hud().align());
	}

	private Notifications n() {
		return draft.notifications();
	}

	private void setN(Notifications v) {
		draft = draft.withNotifications(v);
	}

	private void save() {
		Double hours = optionalNumber(windowText);
		Double max = optionalNumber(maxText);
		if (hours == null || !ConfigCodec.validWindow(hours)) {
			error("Recent window must be a number of hours, 0 to " + (long) T3CraftSettings.MAX_WINDOW_HOURS);
			return;
		}
		if (max == null || !ConfigCodec.validMax(max)) {
			error("Max per environment must be a whole number, 1 to " + T3CraftSettings.MAX_PER_ENV_LIMIT);
			return;
		}
		T3CraftSettings next = draft.withVisibility(hours, max.intValue(), draft.includeSubagents());
		ConfigManager.SaveResult r = ConfigManager.get().update(s -> next);
		draft = ConfigManager.get().settings();
		if (r.ok()) {
			actions.reload();
			ok(r.message() + " and reloaded");
		} else {
			error(r.message());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int colW = Math.min(150, (width - 40) / 3);
		int total = colW * 3 + 12;
		int x0 = width / 2 - total / 2;
		int x1 = x0 + colW + 6;
		int x2 = x1 + colW + 6;
		g.text(font, Component.literal("Notifications").withStyle(ChatFormatting.GRAY), x0, 32, 0xFFFFFFFF);
		g.text(font, Component.literal("Sounds").withStyle(ChatFormatting.GRAY), x1, 32, 0xFFFFFFFF);
		g.text(font, Component.literal("Default filter").withStyle(ChatFormatting.GRAY), x2, 32, 0xFFFFFFFF);
		g.text(font, "Recent window (hours)", x2, 44, 0xFFA0A0A0);
		g.text(font, "Max per environment", x2, 78, 0xFFA0A0A0);
		String mode = "Mode: " + draft.mode() + (draft.mode().equals("bridge")
				? " · bridge key " + (draft.bridgeKey() == null ? "not set" : "set")
				: " · read-only");
		// Next to the "Held terminal screen" toggle (row 5 of the first column).
		String modeLine = mode + " · " + ConfigManager.get().file().getFileName();
		g.text(font, font.plainSubstrByWidth(modeLine, colW - 4), x1 + 4, 44 + 102, 0xFF555555);
	}
}
