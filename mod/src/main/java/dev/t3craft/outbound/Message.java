package dev.t3craft.outbound;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The content of one outgoing message, before a {@link Template} dresses it for a service. Built by
 * {@link #compose} from the events that were coalesced for one destination.
 *
 * @param generic true in "pending only" mode: no thread titles, projects or text, just "something is pending"
 * @param items one rich block per event (chat templates)
 * @param events the events themselves (JSON template); empty when {@code generic}
 */
public record Message(String title, String body, int priority, String tag, int color, @Nullable String click, boolean generic,
		List<Item> items, List<OutboundEvent> events) {

	/** Most blocks in one message (Discord's embed limit). */
	static final int MAX_ITEMS = 10;

	public record Item(String heading, String detail, int color) {
	}

	public Message {
		items = List.copyOf(items);
		events = List.copyOf(events);
	}

	/** Never called with an empty list. */
	static Message compose(Destination d, List<OutboundEvent> events) {
		if (events.isEmpty()) {
			throw new IllegalArgumentException("no events");
		}
		EventType attention = EventType.NEEDS_ATTENTION;
		if (d.pendingOnly()) {
			String line = "Something is pending in T3.";
			return new Message("T3Craft", line, attention.pushPriority, attention.pushTag, attention.color, null, true,
					List.of(new Item(line, "", attention.color)), List.of());
		}
		EventType top = events.get(0).type();
		for (OutboundEvent e : events) {
			if (e.type().pushPriority > top.pushPriority) {
				top = e.type();
			}
		}
		if (events.size() == 1) {
			OutboundEvent e = events.get(0);
			String thread = e.thread() == null ? "" : Text.oneLine(e.thread());
			String title = e.type().label + (thread.isEmpty() ? "" : ": " + Text.clip(thread, 80));
			String detail = Text.oneLine(e.text());
			if (detail.equals(e.type().label)) {
				detail = "";
			}
			detail = Text.clip(detail, 300);
			String heading = describe(e);
			String body = detail.isEmpty() ? heading : heading + "\n" + detail;
			return new Message(title, body, top.pushPriority, top.pushTag, top.color, expandClick(d.clickUrl(), e), false,
					List.of(new Item(Text.clip(heading, 250), detail, e.type().color)), events);
		}
		int shown = events.size() > MAX_ITEMS ? MAX_ITEMS - 1 : events.size();
		List<Item> items = new ArrayList<>();
		StringBuilder body = new StringBuilder();
		for (int i = 0; i < shown; i++) {
			OutboundEvent e = events.get(i);
			String line = describe(e);
			items.add(new Item(e.type().label, Text.clip(line, 300), e.type().color));
			body.append(e.type().label).append(": ").append(line).append('\n');
		}
		int more = events.size() - shown;
		if (more > 0) {
			items.add(new Item("+" + more + " more", "", top.color));
			body.append('+').append(more).append(" more\n");
		}
		return new Message(events.size() + " T3 updates", body.toString().stripTrailing(), top.pushPriority, top.pushTag, top.color, null, false,
				items, events);
	}

	/** "thread · project (environment)", whatever is known. */
	private static String describe(OutboundEvent e) {
		String thread = e.thread() == null || e.thread().isBlank() ? "A thread" : Text.clip(Text.oneLine(e.thread()), 120);
		StringBuilder sb = new StringBuilder(thread);
		if (e.project() != null && !e.project().isBlank()) {
			sb.append(" · ").append(Text.clip(Text.oneLine(e.project()), 80));
		}
		if (e.environment() != null && !e.environment().isBlank()) {
			sb.append(" (").append(Text.clip(Text.oneLine(e.environment()), 40)).append(')');
		}
		return sb.toString();
	}

	/**
	 * Fills {@code {environment}} and {@code {thread}} (the thread id) in the tap-action URL, URL-encoded.
	 * Null when the template is blank or a placeholder has no value.
	 */
	private static @Nullable String expandClick(@Nullable String template, OutboundEvent e) {
		if (template == null) {
			return null;
		}
		String out = template;
		if (out.contains("{environment}")) {
			if (e.environment() == null || e.environment().isBlank()) {
				return null;
			}
			out = out.replace("{environment}", URLEncoder.encode(e.environment(), StandardCharsets.UTF_8));
		}
		if (out.contains("{thread}")) {
			if (e.threadId() == null || e.threadId().isBlank()) {
				return null;
			}
			out = out.replace("{thread}", URLEncoder.encode(e.threadId(), StandardCharsets.UTF_8));
		}
		return out;
	}
}
