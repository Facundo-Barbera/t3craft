package dev.t3craft.client.t3ui.actions;

import com.google.gson.JsonObject;
import dev.t3craft.t3.OperateAction;
import dev.t3craft.t3.OperateKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * {@code orchestration.dispatchCommand} payloads ({@code OrchestrationV2Command},
 * {@code packages/contracts/src/orchestrationV2.ts}) for the thread actions of this package. Mirrors
 * {@code packages/client-runtime/src/operations/commands.ts}: one command per changed setting
 * ({@code updateThreadMetadata} → {@code thread.model-selection.set},
 * {@code setThreadRuntimeMode} → {@code thread.runtime-mode.set},
 * {@code setThreadInteractionMode} → {@code thread.interaction-mode.set}), each with a fresh
 * {@code commandId}. Pure.
 */
public final class ThreadCommands {
	private final Supplier<String> commandIds;

	public ThreadCommands(Supplier<String> commandIds) {
		this.commandIds = commandIds;
	}

	public static ThreadCommands system() {
		return new ThreadCommands(() -> java.util.UUID.randomUUID().toString());
	}

	/**
	 * The {@link OperateKind#THREAD_CONFIGURE} actions that apply {@code pick} to a thread, only for
	 * the {@code changed} dimensions, in the order model → runtime → interaction. Empty when nothing changed.
	 */
	public List<OperateAction> configure(String envId, String threadId, @Nullable String title, ModelPick pick,
			Set<ModelPickerLogic.Dimension> changed) {
		List<OperateAction> out = new ArrayList<>();
		if (changed.contains(ModelPickerLogic.Dimension.MODEL)) {
			JsonObject c = base("thread.model-selection.set", threadId);
			c.add("modelSelection", pick.modelSelection());
			out.add(OperateAction.dispatch(OperateKind.THREAD_CONFIGURE, envId, threadId, title, c));
		}
		if (changed.contains(ModelPickerLogic.Dimension.RUNTIME)) {
			JsonObject c = base("thread.runtime-mode.set", threadId);
			c.addProperty("runtimeMode", pick.runtime().wire);
			out.add(OperateAction.dispatch(OperateKind.THREAD_CONFIGURE, envId, threadId, title, c));
		}
		if (changed.contains(ModelPickerLogic.Dimension.INTERACTION)) {
			JsonObject c = base("thread.interaction-mode.set", threadId);
			c.addProperty("interactionMode", pick.interaction().wire);
			out.add(OperateAction.dispatch(OperateKind.THREAD_CONFIGURE, envId, threadId, title, c));
		}
		return out;
	}

	/** {@code thread.archive {commandId, threadId}} ({@link OperateKind#THREAD_ARCHIVE}, destructive). */
	public OperateAction archive(String envId, String threadId, @Nullable String title) {
		return OperateAction.dispatch(OperateKind.THREAD_ARCHIVE, envId, threadId, title, base("thread.archive", threadId));
	}

	private JsonObject base(String type, String threadId) {
		if (threadId == null || threadId.isBlank()) {
			throw new IllegalArgumentException("threadId is required");
		}
		JsonObject c = new JsonObject();
		c.addProperty("type", type);
		c.addProperty("commandId", commandIds.get());
		c.addProperty("threadId", threadId);
		return c;
	}
}
