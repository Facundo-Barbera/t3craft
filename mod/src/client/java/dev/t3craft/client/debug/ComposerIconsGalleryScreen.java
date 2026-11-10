package dev.t3craft.client.debug;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.compose.ChatSender;
import dev.t3craft.client.chat.compose.ComposerDrafts;
import dev.t3craft.client.chat.compose.ComposerSkin;
import dev.t3craft.client.chat.compose.ComposerState;
import dev.t3craft.client.chat.compose.ComposerView;
import dev.t3craft.client.t3ui.actions.ModelPicker;
import java.util.Base64;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Gallery scenario {@code composer-icons} (#29): the chat composer on an empty screen with the icons of the composer
 * and the model picker: the paperclip, two image thumbnails (hover one for its remove x), the error line with its
 * warning icon, the model chip (provider initial, chevron down / up) and the round send button. The model picker
 * opens over the fake catalog of {@code model-picker} (star favorites, brain by Reasoning, shield by Access).
 *
 * <p>Nothing is sent: the composer runs on {@link ChatSender#preview()} (every send answers "Gallery: nothing is
 * sent.") and an in-memory draft store. The paperclip opens the real file dialog (or the path field); the images
 * attach to this fake thread only.
 */
final class ComposerIconsGalleryScreen extends Screen {
	/** A valid 1x1 PNG: the thumbnails' fake content. */
	private static final byte[] PIXEL = Base64.getDecoder()
			.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

	private final ComposerView composer;
	private final ModelPicker picker;

	ComposerIconsGalleryScreen() {
		super(Component.literal("Composer icons"));
		Font font = Minecraft.getInstance().font;
		ComposerState state = new ComposerState(ChatSender.preview(), System::currentTimeMillis, new ComposerDrafts());
		composer = new ComposerView(state, ChatTheme.warmStudio(), new ComposerView.Host() {
			@Override
			public void enableControl(String envId) {
			}
		});
		composer.setThread(ModelPickerGalleryScreen.ENV, "t-icons", "Composer icons");
		composer.addImage(PIXEL, "screenshot.png");
		composer.addImage(PIXEL, "diagram.png");
		state.editor().set("Hover the paperclip, the thumbnails and the send button.");
		state.setError("Gallery: an example error line (click it to dismiss)");
		picker = ModelPicker.forGallery(font, ModelPickerGalleryScreen.ENV,
				new Gson().fromJson(ModelPickerGalleryScreen.CATALOG, JsonObject.class), pick -> {
				}).composerChip();
		composer.setControls(List.of(new ComposerView.Control() {
			@Override
			public int width(Font f) {
				return picker.chipWidth();
			}

			@Override
			public void render(GuiGraphicsExtractor g, Font f, int x, int y, int mouseX, int mouseY) {
				picker.setBounds(x, y, picker.chipWidth(), ComposerSkin.CHIP_H);
				picker.render(g, mouseX, mouseY);
			}

			@Override
			public boolean mouseClicked(double mx, double my, int button) {
				return picker.mouseClicked(mx, my, button);
			}
		}));
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ChatTheme t = ChatTheme.warmStudio();
		g.fill(0, 0, width, height, t.backdrop());
		g.fillGradient(0, 0, width, height, t.backdropBottom() & 0x00FFFFFF, t.backdropBottom());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int w = Math.min(460, width - 40);
		int h = composer.height(w);
		composer.setMaxWidth(w);
		composer.setBounds(new Rect((width - w) / 2, height - 24 - h, w, h));
		composer.render(g, font, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (picker.isOpen() && picker.mouseClicked(event.x(), event.y(), event.button())) {
			return true; // the popover lies outside the composer: it sees the click first
		}
		return composer.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return composer.mouseDragged(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return composer.mouseReleased() || super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return picker.mouseScrolled(x, y, scrollY) || composer.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (picker.isOpen()) {
			return picker.keyPressed(e) || super.keyPressed(e);
		}
		return composer.keyPressed(e) || super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (picker.isOpen()) {
			return picker.charTyped(event.codepoint()) || super.charTyped(event);
		}
		return composer.charTyped(event) || super.charTyped(event);
	}

	@Override
	public void removed() {
		composer.setFocused(false); // stops the text input it started
		super.removed();
	}
}
