package dev.t3craft.client.outbound;

import dev.t3craft.T3Craft;
import dev.t3craft.client.ModConfig;
import dev.t3craft.outbound.EventType;
import dev.t3craft.outbound.HttpTransport;
import dev.t3craft.outbound.OutboundEmitter;
import dev.t3craft.outbound.OutboundEvent;
import dev.t3craft.outbound.OutboundStore;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Client glue for the outbound events (docs/features/outbound-events.md): turns what
 * {@code hud.Notifier.transition} announces into an {@link OutboundEvent} and hands it to the shared
 * {@link OutboundEmitter}. The emitter works on its own thread and does nothing unless the player turned
 * the feature on, so this call is cheap and never throws.
 *
 * <p>Hot-swap: the only static is the emitter, always of this exact type; {@link #reset()} drops it.
 */
public final class OutboundClient {
	/** {@code Notifier.transition} text: "<thread title> · <project> (<environment label>)". */
	private static final Pattern TEXT = Pattern.compile("^(.*) · (.*) \\(([^()]*)\\)$");

	private static volatile @Nullable OutboundEmitter emitter;

	private OutboundClient() {
	}

	/**
	 * The hook {@code Notifier.transition} calls. {@code npcId} is {@code <environment>:<thread id>},
	 * {@code sound} is attention | done | error. Anything else is not an outbound event.
	 */
	public static void onTransition(@Nullable String npcId, String title, String text, @Nullable String sound) {
		try {
			EventType type = typeOf(sound);
			if (type == null) {
				return;
			}
			String environment = null;
			String threadId = npcId;
			if (npcId != null) {
				int colon = npcId.indexOf(':');
				if (colon > 0) {
					environment = npcId.substring(0, colon);
					threadId = npcId.substring(colon + 1);
				}
			}
			String thread = text;
			String project = null;
			Matcher m = TEXT.matcher(text == null ? "" : text);
			if (m.matches()) {
				thread = m.group(1);
				project = m.group(2);
			}
			emitter().emit(new OutboundEvent(type, environment, threadId, thread, project, type.wire, title == null ? "" : title,
					System.currentTimeMillis()));
		} catch (RuntimeException e) {
			T3Craft.LOGGER.debug("[outbound] could not hand over an event: {}", e.getClass().getSimpleName());
		}
	}

	private static @Nullable EventType typeOf(@Nullable String sound) {
		if (sound == null) {
			return null;
		}
		return switch (sound) {
			case "attention" -> EventType.NEEDS_ATTENTION;
			case "error" -> EventType.ERROR;
			case "done" -> EventType.DONE;
			default -> null;
		};
	}

	/** Sends a test message to a saved destination; {@code done} gets the result on the worker thread. */
	public static void sendTest(String destinationId, Consumer<String> done) {
		emitter().sendTest(destinationId, done);
	}

	/** Stops the worker (the next event starts a new one). */
	public static void reset() {
		synchronized (OutboundClient.class) {
			OutboundEmitter e = emitter;
			emitter = null;
			if (e != null) {
				e.close();
			}
		}
	}

	private static OutboundEmitter emitter() {
		OutboundEmitter e = emitter;
		if (e == null) {
			synchronized (OutboundClient.class) {
				e = emitter;
				if (e == null) {
					OutboundStore store = new OutboundStore(ModConfig.dataDir());
					e = new OutboundEmitter(store::load, store::url, new HttpTransport(),
							msg -> T3Craft.LOGGER.warn("[outbound] {}", msg), OutboundEmitter.Policy.DEFAULT, System::currentTimeMillis);
					emitter = e;
				}
			}
		}
		return e;
	}
}
