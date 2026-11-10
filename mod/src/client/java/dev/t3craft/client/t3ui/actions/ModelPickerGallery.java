package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.t3craft.client.chat.ChatDraw;
import dev.t3craft.client.chat.ChatTheme;
import dev.t3craft.client.chat.Rect;
import dev.t3craft.client.chat.compose.ComposerSkin;
import dev.t3craft.client.t3ui.kit.T3Draw;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The UI gallery's model picker scenarios ({@code /t3dev ui model-picker} and {@code model-picker-thread}): the
 * composer chip alone at the bottom of a screen, over a canned {@code server.getConfig} with thirteen
 * providers (the list scrolls), models with every field the picker reads (badge, aliases, sub-provider, legacy,
 * custom), a reasoning select, an extra select, switches, and a provider that does not support Auto.
 * Favorites live in memory. The thread variant has the Apply / Cancel footer; Apply is aimed at a fake
 * environment and fails with the usual toast.
 */
public final class ModelPickerGallery extends Screen {
	private static final String ENV = "gallery";

	private final boolean thread;
	private final @Nullable Screen parent;
	private @Nullable ModelPicker picker;
	private String last = "(nothing picked yet)";

	public ModelPickerGallery(@Nullable Screen parent, boolean thread) {
		super(Component.literal("Model picker"));
		this.parent = parent;
		this.thread = thread;
	}

	@Override
	protected void init() {
		if (picker == null) {
			picker = ModelPicker.forGallery(font, ENV, config(), thread, p -> last = p.modelSelection() + " / " + p.runtime().wire);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		ChatTheme t = ChatTheme.warmStudio();
		g.fill(0, 0, width, height, t.backdrop());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		ChatTheme t = ChatTheme.warmStudio();
		T3Draw.text(g, font, t, ChatDraw.clip(font, "onPicked: " + last, width - 16), 8, 8, t.text());
		ModelPicker p = picker;
		if (p == null) {
			return;
		}
		Rect bar = new Rect(8, height - 28, width - 16, 20);
		ComposerSkin.box(g, bar.x(), bar.y(), bar.w(), bar.h(), ComposerSkin.BOX, ComposerSkin.BORDER);
		p.setBounds(bar.x() + 6, bar.y() + (bar.h() - ComposerSkin.CHIP_H) / 2, p.chipWidth(), ComposerSkin.CHIP_H);
		p.render(g, mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return (picker != null && picker.mouseClicked(event.x(), event.y(), event.button())) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return (picker != null && picker.mouseScrolled(x, y, scrollY)) || super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		return (picker != null && picker.keyPressed(e)) || super.keyPressed(e);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return (picker != null && picker.charTyped(event.codepoint())) || super.charTyped(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- canned server.getConfig -----------------------------------------------------------------

	/** {@code {providers: [...]}} as {@link ProviderCatalog#fromServerConfig} reads it. */
	static JsonObject config() {
		JsonArray ps = new JsonArray();
		ps.add(provider("claude", "claudeAgent", "Claude", true, new String[] {"approval-required", "auto-accept-edits", "auto", "full-access"},
				model("claude-opus-5-5", "Claude Opus 5.5", "Opus 5.5", true, false, false, "New", null, new String[] {"opus"},
						select("effort", "Effort", "reasoning", "medium", "low", "medium", "high", "xhigh", "max"),
						flag("thinking", "Thinking", true, null),
						flag("fastMode", "Fast mode", null, "Faster output, higher cost")),
				model("claude-sonnet-5-5", "Claude Sonnet 5.5", "Sonnet 5.5", false, false, false, null, null, new String[] {"sonnet"},
						select("effort", "Effort", "reasoning", "medium", "low", "medium", "high")),
				model("claude-haiku-5-5", "Claude Haiku 5.5", "Haiku 5.5", false, false, false, null, null, new String[] {"haiku"}),
				model("claude-opus-4-1", "Claude Opus 4.1", "Opus 4.1", false, true, false, null, null, new String[0],
						select("effort", "Effort", "reasoning", "medium", "low", "medium", "high")),
				model("claude-sonnet-4", "Claude Sonnet 4", "Sonnet 4", false, true, false, null, null, new String[0]),
				model("claude-3-7-sonnet", "Claude 3.7 Sonnet", "Sonnet 3.7", false, true, false, null, null, new String[0]),
				model("team-proxy-model", "Team proxy model", null, false, false, true, null, "Bedrock", new String[] {"proxy"})));
		ps.add(provider("codex", "codex", "Codex", false, new String[] {"approval-required", "auto-accept-edits", "full-access"},
				model("gpt-5-codex", "GPT-5 Codex", "GPT-5 Codex", true, false, false, null, null, new String[] {"codex"},
						select("reasoningEffort", "Reasoning effort", "reasoning", "medium", "minimal", "low", "medium", "high"),
						select("verbosity", "Verbosity", null, "medium", "low", "medium", "high"),
						flag("fast", "Fast", false, "Priority processing")),
				model("gpt-5", "GPT-5", null, false, false, false, null, null, new String[0],
						select("reasoningEffort", "Reasoning effort", "reasoning", "medium", "minimal", "low", "medium", "high")),
				model("gpt-5-mini", "GPT-5 mini", null, false, false, false, null, null, new String[0]),
				model("gpt-4-1", "GPT-4.1", null, false, true, false, null, null, new String[0])));
		ps.add(provider("gemini", "gemini", "Gemini", false, new String[] {"approval-required", "full-access"},
				model("gemini-2-5-pro", "Gemini 2.5 Pro", null, true, false, false, null, null, new String[0],
						select("thinkingLevel", "Thinking level", null, null, "low", "high")),
				model("gemini-2-5-flash", "Gemini 2.5 Flash", null, false, false, false, null, null, new String[0])));
		ps.add(provider("opencode", "opencode", "OpenCode", false, new String[0],
				model("anthropic/claude-sonnet", "Claude Sonnet", null, true, false, false, null, "Anthropic", new String[0]),
				model("openai/gpt-5", "GPT-5", null, false, false, false, null, "OpenAI", new String[0]),
				model("google/gemini-pro", "Gemini Pro", null, false, false, false, null, "Google", new String[0])));
		String[] more = {"Cursor", "Ollama", "OpenRouter", "Mistral", "Grok", "Kimi", "DeepSeek", "Qwen", "Local"};
		for (String name : more) {
			String id = name.toLowerCase(Locale.ROOT);
			ps.add(provider(id, id, name, false, new String[0], model(id + "-large", name + " Large", null, true, false, false, null, null, new String[0]),
					model(id + "-small", name + " Small", null, false, false, false, null, null, new String[0])));
		}
		JsonObject cfg = new JsonObject();
		cfg.add("providers", ps);
		return cfg;
	}

	private static JsonObject provider(String id, String driver, String name, boolean planToggle, String[] runtimes, JsonObject... models) {
		JsonObject o = new JsonObject();
		o.addProperty("instanceId", id);
		o.addProperty("driver", driver);
		o.addProperty("displayName", name);
		o.addProperty("enabled", true);
		o.addProperty("installed", true);
		o.addProperty("availability", "available");
		o.addProperty("showInteractionModeToggle", planToggle);
		JsonArray rm = new JsonArray();
		for (String r : runtimes) {
			rm.add(r);
		}
		o.add("supportedRuntimeModes", rm);
		JsonArray ms = new JsonArray();
		for (JsonObject m : models) {
			ms.add(m);
		}
		o.add("models", ms);
		return o;
	}

	private static JsonObject model(String slug, String name, @Nullable String shortName, boolean isDefault, boolean legacy, boolean custom,
			@Nullable String badge, @Nullable String subProvider, String[] aliases, JsonObject... descriptors) {
		JsonObject o = new JsonObject();
		o.addProperty("slug", slug);
		o.addProperty("name", name);
		if (shortName != null) {
			o.addProperty("shortName", shortName);
		}
		o.addProperty("isDefault", isDefault);
		o.addProperty("isLegacy", legacy);
		o.addProperty("isCustom", custom);
		if (badge != null) {
			o.addProperty("badge", badge);
		}
		if (subProvider != null) {
			o.addProperty("subProvider", subProvider);
		}
		JsonArray al = new JsonArray();
		for (String a : aliases) {
			al.add(a);
		}
		o.add("aliases", al);
		JsonArray ds = new JsonArray();
		for (JsonObject d : descriptors) {
			ds.add(d);
		}
		JsonObject caps = new JsonObject();
		caps.add("optionDescriptors", ds);
		o.add("capabilities", caps);
		return o;
	}

	/** A select descriptor; {@code defaultId} null = no default choice (the picker shows "Default"). */
	private static JsonObject select(String id, String label, @Nullable String role, @Nullable String defaultId, String... choices) {
		JsonObject d = new JsonObject();
		d.addProperty("id", id);
		d.addProperty("label", label);
		d.addProperty("type", "select");
		if (role != null) {
			d.addProperty("role", role);
		}
		JsonArray opts = new JsonArray();
		for (String c : choices) {
			JsonObject co = new JsonObject();
			co.addProperty("id", c);
			co.addProperty("label", Character.toUpperCase(c.charAt(0)) + c.substring(1));
			if (c.equals(defaultId)) {
				co.addProperty("isDefault", true);
			}
			opts.add(co);
		}
		d.add("options", opts);
		return d;
	}

	private static JsonObject flag(String id, String label, @Nullable Boolean current, @Nullable String description) {
		JsonObject d = new JsonObject();
		d.addProperty("id", id);
		d.addProperty("label", label);
		d.addProperty("type", "boolean");
		if (current != null) {
			d.addProperty("currentValue", current);
		}
		if (description != null) {
			d.addProperty("description", description);
		}
		return d;
	}
}
