package dev.t3craft.client.screen;

import dev.t3craft.client.T3CraftClient;
import dev.t3craft.client.operate.LaunchClient;
import dev.t3craft.client.operate.OperateClient;
import dev.t3craft.client.operate.OperateUi;
import dev.t3craft.client.search.SearchClient;
import dev.t3craft.t3.OperateGate;
import dev.t3craft.t3.OperateResult;
import dev.t3craft.t3.ThreadLaunch;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * "New thread" (item 23, feat/op-launch): pick an environment (only those with control/operate),
 * a project (read-only from that environment's shell) and a model (optional: the ones the shell
 * exposes, else the project's default), write a multi-line prompt, review a summary and launch.
 * Not destructive, so no confirmation screen, but "Launch" only appears on the summary page. The
 * write goes through {@link LaunchClient#launch} → {@code OperateUi.run}; the new NPC appears
 * through the normal shell flow.
 *
 * <p>Entry points: {@link #open(String, String)} (blocks such as a lectern/board call this with a
 * project preselected), the portable terminal's "New thread…" button.
 */
public final class NewThreadScreen extends Screen {
	private static final int W = 360;

	private final @Nullable Screen parent;
	private @Nullable String env;
	private @Nullable String projectId;
	/** Null = project default. */
	private ThreadLaunch.@Nullable ModelChoice model;
	private String prompt = "";
	private boolean review;
	private boolean busy;
	private @Nullable OperateResult result;
	private @Nullable String resultEnv;
	private Component message = Component.empty();

	private @Nullable MultiLineEditBox promptBox;

	public NewThreadScreen(@Nullable Screen parent, @Nullable String env, @Nullable String projectId) {
		super(Component.translatable("t3craft.operate.launch.title"));
		this.parent = parent;
		this.env = env;
		this.projectId = projectId;
	}

	/**
	 * Opens the screen with {@code env} and {@code projectId} preselected (either may be null).
	 * Hook for blocks (feat/blocks-core): {@code NewThreadScreen.open(env, projectId)}. Client thread.
	 */
	public static void open(@Nullable String env, @Nullable String projectId) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(new NewThreadScreen(mc.gui.screen(), env, projectId));
	}

	// ---- choices --------------------------------------------------------------------------------

	private List<String> envs() {
		return LaunchClient.operableEnvs();
	}

	private List<ThreadLaunch.ProjectChoice> projects() {
		return env == null ? List.of() : LaunchClient.projects(env);
	}

	private List<ThreadLaunch.ModelChoice> models() {
		return env == null || projectId == null ? List.of() : ThreadLaunch.models(LaunchClient.view(env), projectId);
	}

	/** Keeps the selection valid (env with operate, known project, exposed model). */
	private void normalize() {
		List<String> es = envs();
		if (env == null || !es.contains(env)) {
			env = es.isEmpty() ? null : es.getFirst();
		}
		List<ThreadLaunch.ProjectChoice> ps = projects();
		if (projectId == null || ps.stream().noneMatch(p -> p.id().equals(projectId))) {
			projectId = ps.isEmpty() ? null : ps.getFirst().id();
		}
		if (model != null && !models().contains(model)) {
			model = null;
		}
	}

	private static <T> @Nullable T next(List<T> list, @Nullable T current) {
		if (list.isEmpty()) {
			return null;
		}
		int i = list.indexOf(current);
		return list.get((i + 1) % list.size());
	}

	private ThreadLaunch.@Nullable ProjectChoice project() {
		return projects().stream().filter(p -> p.id().equals(projectId)).findFirst().orElse(null);
	}

	private Component modelLabel() {
		if (model != null) {
			return Component.literal(model.label());
		}
		ThreadLaunch.ModelChoice d = env == null || projectId == null ? null : ThreadLaunch.resolveModel(LaunchClient.view(env), projectId, null);
		return d == null ? Component.translatable("t3craft.operate.launch.model.none")
				: Component.translatable("t3craft.operate.launch.model.default", d.label());
	}

	// ---- layout ---------------------------------------------------------------------------------

	private int left() {
		return width / 2 - w() / 2;
	}

	private int w() {
		return Math.min(W, width - 32);
	}

	@Override
	protected void init() {
		if (promptBox != null) {
			prompt = promptBox.getValue();
		}
		promptBox = null;
		normalize();
		int x = left();
		int w = w();
		int y = 34;
		int half = w / 2 - 4;
		if (!review) {
			addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.launch.env", env == null ? "—" : OperateClient.label(env)), b -> {
				env = next(envs(), env);
				projectId = null;
				model = null;
				rebuildWidgets();
			}).bounds(x, y, w, 20).build()).active = envs().size() > 1;
			y += 24;
			ThreadLaunch.ProjectChoice p = project();
			addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.launch.project", p == null ? "—" : p.label()), b -> {
				projectId = Objects.requireNonNullElse(next(projects().stream().map(ThreadLaunch.ProjectChoice::id).toList(), projectId), projectId);
				model = null;
				rebuildWidgets();
			}).bounds(x, y, w, 20).build()).active = projects().size() > 1;
			y += 24;
			addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.launch.model", modelLabel()), b -> {
				List<ThreadLaunch.ModelChoice> ms = models();
				int i = model == null ? -1 : ms.indexOf(model);
				model = i + 1 < ms.size() ? ms.get(i + 1) : null; // cycles back to "project default"
				rebuildWidgets();
			}).bounds(x, y, w, 20).build()).active = !models().isEmpty();
			y += 34;
			int boxH = Math.max(40, height - y - 64);
			MultiLineEditBox box = MultiLineEditBox.builder().setX(x).setY(y)
					.setPlaceholder(Component.translatable("t3craft.operate.launch.prompt.hint").withStyle(ChatFormatting.DARK_GRAY))
					.build(font, w, boxH, Component.translatable("t3craft.operate.launch.prompt"));
			box.setCharacterLimit(ThreadLaunch.MAX_PROMPT);
			box.setValue(prompt);
			box.setValueListener(v -> prompt = v);
			promptBox = addRenderableWidget(box);
			y += boxH + 8;
			Button reviewButton = addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.launch.review"), b -> toReview())
					.bounds(x, y, half, 20).build());
			reviewButton.active = env != null && projectId != null && !busy;
			if (env != null) {
				OperateUi.guard(reviewButton, env);
			}
			addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + w - half, y, half, 20).build());
			setInitialFocus(box);
		} else {
			int by = height - 52;
			Button launch = addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.launch.button").withStyle(ChatFormatting.GOLD), b -> launch())
					.bounds(x, by, half, 20).build());
			launch.active = !busy && result == null;
			if (env != null) {
				OperateUi.guard(launch, env);
			}
			addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.launch.back"), b -> {
				review = false;
				result = null;
				message = Component.empty();
				rebuildWidgets();
			}).bounds(x + w - half, by, half, 20).build()).active = !busy;
			String newThread = result == null ? null : ThreadLaunch.threadId(result);
			if (newThread != null && resultEnv != null) {
				String openEnv = resultEnv;
				addRenderableWidget(Button.builder(Component.translatable("t3craft.operate.launch.open"), b -> {
					T3CraftClient c = T3CraftClient.get();
					if (c != null) {
						SearchClient.open(c, openEnv, newThread);
					}
				}).bounds(x, by + 24, w, 20).build());
			}
		}
	}

	private void toReview() {
		String err = ThreadLaunch.validatePrompt(prompt);
		if (err != null) {
			message = Component.literal(err).withStyle(ChatFormatting.RED);
			return;
		}
		message = Component.empty();
		result = null;
		review = true;
		rebuildWidgets();
	}

	private void launch() {
		if (busy || env == null || projectId == null) {
			return;
		}
		busy = true;
		String launchEnv = env;
		message = Component.translatable("t3craft.operate.launch.sending").withStyle(ChatFormatting.YELLOW);
		rebuildWidgets();
		LaunchClient.launch(new ThreadLaunch.Request(launchEnv, projectId, prompt, model), OperateGate.Origin.UI, this, r -> {
			busy = false;
			result = r;
			resultEnv = launchEnv;
			message = Component.literal(r.ok() ? "Launched." : r.outcome().wire + ": " + r.message())
					.withStyle(r.ok() ? ChatFormatting.GREEN : ChatFormatting.RED);
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.sendSystemMessage(LaunchClient.resultLine(launchEnv, r)); // clickable "[open chat]" link
			}
			if (r.ok()) {
				prompt = "";
			}
			rebuildWidgets();
		});
	}

	// ---- render ---------------------------------------------------------------------------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		int x = left();
		int w = w();
		g.centeredText(font, title.copy().withStyle(ChatFormatting.YELLOW), width / 2, 14, 0xFFFFFFFF);
		// Why "Review" is disabled, on the message line under the buttons. It used to be drawn at
		// height / 2 - 30 (inside the prompt box) or at y 24 (under the environment button).
		Component why = env == null ? Component.translatable("t3craft.operate.launch.no_envs")
				: projectId == null ? Component.translatable("t3craft.operate.launch.no_projects") : null;
		Component bottom = message.getString().isEmpty() && why != null ? why.copy().withStyle(ChatFormatting.GRAY) : message;
		if (!review) {
			if (promptBox != null) {
				g.text(font, Component.translatable("t3craft.operate.launch.prompt"), x, promptBox.getY() - 10, 0xFFA0A0A0);
			}
		} else {
			int y = 36;
			ThreadLaunch.ProjectChoice p = project();
			y = drawWrapped(g, Component.translatable("t3craft.operate.launch.summary").withStyle(ChatFormatting.GOLD), x, y, w) + 4;
			y = drawWrapped(g, Component.translatable("t3craft.operate.launch.env", env == null ? "—" : OperateClient.label(env)), x, y, w);
			y = drawWrapped(g, Component.translatable("t3craft.operate.launch.project", p == null ? "—" : p.label()), x, y, w);
			y = drawWrapped(g, Component.translatable("t3craft.operate.launch.model", modelLabel()), x, y, w);
			y = drawWrapped(g, Component.translatable("t3craft.operate.launch.summary.title", ThreadLaunch.title(prompt)), x, y, w);
			y = drawWrapped(g, Component.translatable("t3craft.operate.launch.summary.prompt", ThreadLaunch.promptSummary(prompt))
					.withStyle(ChatFormatting.GRAY), x, y, w) + 4;
			y = drawPreview(g, x, y, w, Math.max(0, height - 64 - y - 30));
			drawWrapped(g, Component.translatable("t3craft.operate.launch.summary.mode").withStyle(ChatFormatting.DARK_GRAY), x, y + 4, w);
		}
		// Review page: above the Launch/Back row, since "Open chat" (height - 28) covered height - 24.
		int my = review ? height - 64 : height - 24;
		int limit = review ? height - 52 : height;
		for (FormattedCharSequence s : font.split(bottom, w)) {
			if (my + font.lineHeight > limit) {
				break;
			}
			g.centeredText(font, s, width / 2, my, 0xFFFFFFFF);
			my += font.lineHeight;
		}
	}

	/** The first lines of the prompt, as far as they fit in {@code maxH}. */
	private int drawPreview(GuiGraphicsExtractor g, int x, int y, int w, int maxH) {
		int lines = maxH / font.lineHeight;
		List<FormattedCharSequence> all = font.split(Component.literal(prompt).withStyle(ChatFormatting.WHITE), w - 8);
		for (int i = 0; i < Math.min(lines, all.size()); i++) {
			boolean last = i == lines - 1 && all.size() > lines;
			g.text(font, last ? Component.literal("…").getVisualOrderText() : all.get(i), x + 8, y, 0xFFFFFFFF);
			y += font.lineHeight;
		}
		return y;
	}

	private int drawWrapped(GuiGraphicsExtractor g, Component c, int x, int y, int w) {
		for (FormattedCharSequence s : font.split(c, w)) {
			g.text(font, s, x, y, 0xFFFFFFFF);
			y += font.lineHeight;
		}
		return y;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
