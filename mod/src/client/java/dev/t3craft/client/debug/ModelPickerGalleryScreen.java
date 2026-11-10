package dev.t3craft.client.debug;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.compose.ComposerSkin;
import dev.t3craft.client.t3ui.actions.ModelPicker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Gallery scenario {@code model-picker}: the composer's model chip at the bottom right of an empty screen with its
 * popover open, over a fake catalog that has what the picker draws: a reasoning select with and without
 * {@code role}, a second select, switches, legacy models, badges, sub-providers, a custom model and more
 * providers than rows. Nothing is sent anywhere; favorites are real (they write {@code model-favorites.json}).
 */
final class ModelPickerGalleryScreen extends Screen {
	private static final String ENV = "t3dev-gallery";

	private static final String CATALOG = """
			{"providers": [
			 {"instanceId": "claudeAgent", "driver": "claudeAgent", "displayName": "Claude",
			  "supportedRuntimeModes": ["approval-required", "auto-accept-edits", "auto", "full-access"],
			  "models": [
			   {"slug": "claude-opus-5-5", "name": "Claude Opus 5.5", "shortName": "Opus 5.5", "isDefault": true, "badge": "New",
			    "capabilities": {"optionDescriptors": [
			     {"type": "select", "id": "effort", "label": "Reasoning", "role": "reasoning", "options": [
			      {"id": "low", "label": "Low"}, {"id": "medium", "label": "Medium", "isDefault": true}, {"id": "high", "label": "High"}, {"id": "max", "label": "Max"}]},
			     {"type": "select", "id": "context", "label": "Context", "options": [{"id": "200k", "label": "200k", "isDefault": true}, {"id": "1m", "label": "1M"}]},
			     {"type": "boolean", "id": "fastMode", "label": "Fast mode", "description": "Faster output at a higher price."},
			     {"type": "boolean", "id": "thinking", "label": "Extended thinking", "currentValue": true}]}},
			   {"slug": "claude-sonnet-5-5", "name": "Claude Sonnet 5.5", "shortName": "Sonnet 5.5",
			    "capabilities": {"optionDescriptors": [{"type": "select", "id": "effort", "label": "Effort", "options": [
			     {"id": "low", "label": "Low"}, {"id": "high", "label": "High", "isDefault": true}]}]}},
			   {"slug": "claude-haiku-5-5", "name": "Claude Haiku 5.5", "shortName": "Haiku 5.5"},
			   {"slug": "claude-opus-4-1", "name": "Claude Opus 4.1", "shortName": "Opus 4.1", "isLegacy": true, "aliases": ["opus-4"]},
			   {"slug": "claude-sonnet-4", "name": "Claude Sonnet 4", "shortName": "Sonnet 4", "isLegacy": true}]},
			 {"instanceId": "codex", "driver": "codex", "displayName": "Codex",
			  "supportedRuntimeModes": ["approval-required", "full-access"],
			  "models": [
			   {"slug": "gpt-5.5", "name": "GPT-5.5", "shortName": "5.5", "isDefault": true,
			    "capabilities": {"optionDescriptors": [
			     {"type": "select", "id": "verbosity", "label": "Verbosity", "options": [{"id": "low", "label": "Low"}, {"id": "high", "label": "High"}]},
			     {"type": "select", "id": "reasoningEffort", "label": "Reasoning", "options": [
			      {"id": "low", "label": "Low"}, {"id": "medium", "label": "Medium", "isDefault": true}, {"id": "high", "label": "High"}]}]}},
			   {"slug": "gpt-5.5-codex", "name": "GPT-5.5 Codex", "shortName": "5.5 Codex"},
			   {"slug": "my-fine-tune", "name": "My fine-tune", "isCustom": true, "subProvider": "azure"}]},
			 {"instanceId": "gemini", "driver": "gemini", "displayName": "Gemini", "models": [{"slug": "gemini-3", "name": "Gemini 3", "isDefault": true}]},
			 {"instanceId": "cursor", "driver": "cursor", "displayName": "Cursor", "models": [{"slug": "auto", "name": "Auto", "isDefault": true}]},
			 {"instanceId": "opencode", "driver": "opencode", "displayName": "OpenCode", "models": [{"slug": "o1", "name": "Model one", "isDefault": true}]},
			 {"instanceId": "grok", "driver": "grok", "displayName": "Grok", "models": [{"slug": "g1", "name": "Grok 1", "isDefault": true}]},
			 {"instanceId": "mistral", "driver": "mistral", "displayName": "Mistral", "models": [{"slug": "m1", "name": "Mistral Large", "isDefault": true}]},
			 {"instanceId": "kimi", "driver": "kimi", "displayName": "Kimi", "models": [{"slug": "k1", "name": "Kimi K", "isDefault": true}]},
			 {"instanceId": "qwen", "driver": "qwen", "displayName": "Qwen", "models": [{"slug": "q1", "name": "Qwen Max", "isDefault": true}]},
			 {"instanceId": "local", "driver": "ollama", "displayName": "Local", "models": [{"slug": "l1", "name": "Llama local", "isDefault": true}]},
			 {"instanceId": "azure", "driver": "azure", "displayName": "Azure", "models": [{"slug": "a1", "name": "Azure one", "isDefault": true}]}
			]}
			""";

	private final ModelPicker picker;
	private boolean opened;

	ModelPickerGalleryScreen() {
		super(Component.literal("Model picker"));
		this.picker = ModelPicker.forGallery(Minecraft.getInstance().font, ENV, new Gson().fromJson(CATALOG, JsonObject.class), pick -> {
		}).composerChip();
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
		int cw = picker.chipWidth();
		picker.setBounds(width - cw - 24, height - 36, cw, ComposerSkin.CHIP_H);
		if (!opened) {
			opened = true;
			picker.render(g, mouseX, mouseY); // the catalog loads on the first frame
			picker.toggle();
		}
		picker.render(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return picker.mouseClicked(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return picker.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		return picker.keyPressed(e) || super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return picker.charTyped(event.codepoint()) || super.charTyped(event);
	}
}
