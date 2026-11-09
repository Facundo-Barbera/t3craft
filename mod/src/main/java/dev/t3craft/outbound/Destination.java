package dev.t3craft.outbound;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * One place events are sent to, with its own event filter. The webhook URL is <b>not</b> part of this
 * record: it is a secret and lives in {@link OutboundStore} as a credential, found by {@code id}.
 *
 * @param id slug ({@link #ID}); names the credential file, so it never holds path characters
 * @param name label shown in the settings screen
 * @param enabled this destination only; the master switch is {@link OutboundConfig#enabled()}
 * @param events the event types this destination receives
 * @param pendingOnly send only "something is pending" (needs-input events, no titles, no text, no thread)
 * @param clickUrl push tap action; {@code {environment}} and {@code {thread}} are filled in. Not a secret.
 */
public record Destination(String id, String name, Template template, boolean enabled, Set<EventType> events, boolean pendingOnly,
		@Nullable String clickUrl) {

	public static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9-]{0,31}");
	public static final int MAX_NAME = 40;
	public static final int MAX_CLICK = 300;

	public Destination {
		if (id == null || !ID.matcher(id).matches()) {
			throw new IllegalArgumentException("invalid destination id");
		}
		String shown = name == null ? "" : Text.oneLine(name);
		name = Text.clip(shown.isEmpty() ? id : shown, MAX_NAME);
		template = template == null ? Template.PUSH : template;
		EnumSet<EventType> copy = EnumSet.noneOf(EventType.class);
		if (events != null) {
			copy.addAll(events);
		}
		events = Collections.unmodifiableSet(copy);
		clickUrl = clickUrl == null || clickUrl.isBlank() ? null : Text.clip(clickUrl.strip(), MAX_CLICK);
	}

	/**
	 * A new destination. Private by default: push template, the three events that need the player, and
	 * "pending only" on, so no thread title leaves the machine until the player turns that off.
	 */
	public static Destination create(String id, String name) {
		return new Destination(id, name, Template.PUSH, true,
				EnumSet.of(EventType.NEEDS_ATTENTION, EventType.ERROR, EventType.DONE), true, null);
	}

	/** First free id of the form {@code dest-1}, {@code dest-2}… */
	public static String freeId(Collection<String> taken) {
		for (int i = 1; ; i++) {
			String id = "dest-" + i;
			if (!taken.contains(id)) {
				return id;
			}
		}
	}

	/** Whether an event of this type is sent here (not counting the master switch). */
	public boolean accepts(EventType type) {
		if (!enabled || !events.contains(type)) {
			return false;
		}
		return !pendingOnly || type == EventType.NEEDS_ATTENTION;
	}

	public Destination withName(String v) {
		return new Destination(id, v, template, enabled, events, pendingOnly, clickUrl);
	}

	public Destination withTemplate(Template v) {
		return new Destination(id, name, v, enabled, events, pendingOnly, clickUrl);
	}

	public Destination withEnabled(boolean v) {
		return new Destination(id, name, template, v, events, pendingOnly, clickUrl);
	}

	public Destination withEvents(Set<EventType> v) {
		return new Destination(id, name, template, enabled, v, pendingOnly, clickUrl);
	}

	public Destination withEvent(EventType type, boolean on) {
		EnumSet<EventType> copy = EnumSet.noneOf(EventType.class);
		copy.addAll(events);
		if (on) {
			copy.add(type);
		} else {
			copy.remove(type);
		}
		return withEvents(copy);
	}

	public Destination withPendingOnly(boolean v) {
		return new Destination(id, name, template, enabled, events, v, clickUrl);
	}

	public Destination withClickUrl(@Nullable String v) {
		return new Destination(id, name, template, enabled, events, pendingOnly, v);
	}
}
