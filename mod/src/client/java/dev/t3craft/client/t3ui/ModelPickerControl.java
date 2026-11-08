package dev.t3craft.client.t3ui;

import dev.t3craft.client.chat.compose.ComposerSkin;
import dev.t3craft.client.chat.compose.ComposerView;
import dev.t3craft.client.t3ui.actions.ModelPick;
import dev.t3craft.client.t3ui.actions.ModelPicker;
import dev.t3craft.client.t3ui.actions.Modes;
import dev.t3craft.t3.ThreadShell;
import java.util.function.Supplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * feat/t3-actions' {@link ModelPicker} as the composer's model chip (T3's {@code ComposerModelPicker}:
 * "✳ Opus 5.5 · Medium", the popover opening upwards). One picker per thread, rebuilt when the
 * selected thread changes, with the thread's model from its shell ({@link ModelPick#fromMeta}); on
 * a draft thread it is the new-thread picker of the draft's environment and its choice goes into
 * the draft. {@link #access()} is T3's access chip, shown only when the access differs from the
 * default (as {@code ComposerFooterModeControls}); it opens the same popover. The popup lies outside
 * the control's rectangle, so the host routes input to it while it is open ({@link T3Slots.Popup}).
 */
final class ModelPickerControl implements ComposerView.Control, T3Slots.Popup {
	private final Supplier<T3Slots.@Nullable Target> target;
	private final Supplier<@Nullable ThreadDraft> draft;
	private @Nullable ModelPicker picker;
	private @Nullable String pickerKey;
	private int lastW = 60;

	ModelPickerControl(Supplier<T3Slots.@Nullable Target> target, Supplier<@Nullable ThreadDraft> draft) {
		this.target = target;
		this.draft = draft;
	}

	ModelPickerControl(Supplier<T3Slots.@Nullable Target> target) {
		this(target, () -> null);
	}

	private @Nullable ModelPicker picker(Font font) {
		ThreadDraft d = draft.get();
		if (d != null) {
			String k = "draft:" + d.id;
			if (!k.equals(pickerKey) || picker == null) {
				ThreadDraft dd = d;
				picker = ModelPicker.forNewThread(font, d.envId, pick -> dd.pick = pick).composerChip();
				pickerKey = k;
				d.pick = picker.pick();
			}
			picker.setEnv(d.envId); // the draft moved to another environment: its catalog and default
			if (d.pick == null) {
				d.pick = picker.pick();
			}
			return picker;
		}
		T3Slots.Target t = target.get();
		String k = t == null ? null : t.envId() + ":" + t.threadId();
		if (k == null) {
			picker = null;
			pickerKey = null;
			return null;
		}
		if (!k.equals(pickerKey) || picker == null) {
			ThreadShell shell = T3Slots.shellOf(t.envId(), t.threadId());
			picker = ModelPicker.forThread(font, t.envId(), t.threadId(), t.title(), shell == null ? null : ModelPick.fromMeta(shell.meta()), pick -> {
			}).composerChip();
			pickerKey = k;
		}
		return picker;
	}

	@Override
	public int width(Font font) {
		ModelPicker p = picker(font);
		lastW = p == null ? 0 : p.chipWidth();
		return lastW;
	}

	@Override
	public boolean visible() {
		return picker != null || target.get() != null || draft.get() != null;
	}

	@Override
	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, int mouseY) {
		ModelPicker p = picker(font);
		if (p != null) {
			p.setBounds(x, y, lastW, ComposerSkin.CHIP_H);
			p.render(g, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		return picker != null && picker.mouseClicked(mx, my, button);
	}

	@Override
	public boolean isOpen() {
		return picker != null && picker.isOpen();
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		return picker != null && picker.keyPressed(e);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double dy) {
		return picker != null && picker.mouseScrolled(mx, my, dy);
	}

	/** T3's access chip ("Full access", "Auto"…): only when not the default; opens the picker. */
	ComposerView.Control access() {
		return new ComposerView.Control() {
			private Modes.@Nullable Runtime current() {
				ModelPicker p = picker;
				return p == null ? null : p.runtime();
			}

			@Override
			public boolean visible() {
				Modes.Runtime r = current();
				return r != null && r != Modes.Runtime.DEFAULT;
			}

			@Override
			public int width(Font font) {
				Modes.Runtime r = current();
				return r == null ? 0 : ComposerSkin.chipWidth(font, "", r.label, "");
			}

			@Override
			public void render(GuiGraphicsExtractor g, Font font, int x, int y, int mouseX, int mouseY) {
				Modes.Runtime r = current();
				if (r == null) {
					return;
				}
				int w = width(font);
				boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ComposerSkin.CHIP_H;
				int color = r == Modes.Runtime.FULL_ACCESS ? ComposerSkin.WARN : ComposerSkin.MUTED;
				ComposerSkin.chip(g, font, x, y, "", 0, r.label, color, "", hover, false);
				if (hover) {
					g.setTooltipForNextFrame(font, Component.literal("Access · " + r.description), mouseX, mouseY);
				}
			}

			@Override
			public boolean mouseClicked(double mx, double my, int button) {
				if (picker != null) {
					picker.toggle();
				}
				return true;
			}
		};
	}
}
