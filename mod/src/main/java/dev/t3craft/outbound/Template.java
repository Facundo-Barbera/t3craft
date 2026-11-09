package dev.t3craft.outbound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * How a {@link Message} is dressed for a service. {@code wire} is the id stored in {@code outbound.json}.
 * <ul>
 *   <li>{@link #PUSH}: a push-notification service (ntfy style): plain-text body, headers
 *   {@code Title}, {@code Priority}, {@code Tags} and {@code Click} (tap action).</li>
 *   <li>{@link #CHAT}: a chat webhook: text plus rich blocks, no buttons (webhooks that do not belong to
 *   an app cannot have them). Slack format when the host is {@code *.slack.com}, Discord format otherwise.</li>
 *   <li>{@link #JSON}: a plain JSON document for automations (Home Assistant, n8n…).</li>
 * </ul>
 */
public enum Template {
	PUSH("push", "Push"),
	CHAT("chat", "Chat"),
	JSON("json", "JSON");

	public final String wire;
	public final String label;

	Template(String wire, String label) {
		this.wire = wire;
		this.label = label;
	}

	public static Template fromWire(@Nullable String s) {
		for (Template t : values()) {
			if (t.wire.equals(s)) {
				return t;
			}
		}
		return PUSH;
	}

	public Template next() {
		Template[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	Request render(URI url, Message m) {
		return switch (this) {
			case PUSH -> push(m);
			case CHAT -> chat(url, m);
			case JSON -> json(m);
		};
	}

	private static Request push(Message m) {
		Map<String, String> h = new LinkedHashMap<>();
		h.put("Title", HeaderText.encode(m.title(), 120));
		h.put("Priority", Integer.toString(m.priority()));
		h.put("Tags", m.tag());
		String click = m.click();
		if (click != null && HeaderText.isPlain(click)) {
			h.put("Click", click);
		}
		return new Request("text/plain; charset=utf-8", h, m.body());
	}

	private static Request chat(URI url, Message m) {
		String host = url.getHost() == null ? "" : url.getHost().toLowerCase(Locale.ROOT);
		JsonObject o = host.endsWith("slack.com") ? slack(m) : discord(m);
		return new Request("application/json", Map.of(), o.toString());
	}

	/** Discord: {@code content} up to 2000, up to 10 embeds, no mentions allowed. */
	private static JsonObject discord(Message m) {
		JsonObject o = new JsonObject();
		o.addProperty("username", "T3Craft");
		o.addProperty("content", Text.clip(m.title(), 2000));
		JsonArray embeds = new JsonArray();
		for (Message.Item it : m.items()) {
			if (embeds.size() >= Message.MAX_ITEMS) {
				break;
			}
			JsonObject e = new JsonObject();
			e.addProperty("title", Text.clip(it.heading(), 256));
			if (!it.detail().isEmpty()) {
				e.addProperty("description", Text.clip(it.detail(), 4096));
			}
			e.addProperty("color", it.color());
			embeds.add(e);
		}
		if (embeds.size() > 0) {
			o.add("embeds", embeds);
		}
		// Thread titles are free text: "@everyone" in one must not ping the channel.
		JsonObject mentions = new JsonObject();
		mentions.add("parse", new JsonArray());
		o.add("allowed_mentions", mentions);
		return o;
	}

	/** Slack: {@code text} fallback plus one {@code section} block per item. */
	private static JsonObject slack(Message m) {
		JsonObject o = new JsonObject();
		o.addProperty("text", slackEscape(m.title()));
		JsonArray blocks = new JsonArray();
		for (Message.Item it : m.items()) {
			if (blocks.size() >= 20) {
				break;
			}
			String s = "*" + slackEscape(it.heading()).replace("*", "") + "*"
					+ (it.detail().isEmpty() ? "" : "\n" + slackEscape(it.detail()));
			JsonObject text = new JsonObject();
			text.addProperty("type", "mrkdwn");
			text.addProperty("text", Text.clip(s, 3000));
			JsonObject section = new JsonObject();
			section.addProperty("type", "section");
			section.add("text", text);
			blocks.add(section);
		}
		o.add("blocks", blocks);
		return o;
	}

	private static String slackEscape(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static Request json(Message m) {
		JsonObject o = new JsonObject();
		o.addProperty("source", "t3craft");
		o.addProperty("title", m.title());
		o.addProperty("body", m.body());
		o.addProperty("pendingOnly", m.generic());
		JsonArray events = new JsonArray();
		for (OutboundEvent e : m.events()) {
			JsonObject j = new JsonObject();
			j.addProperty("type", e.type().wire);
			j.addProperty("environment", e.environment());
			j.addProperty("threadId", e.threadId());
			j.addProperty("thread", e.thread());
			j.addProperty("project", e.project());
			j.addProperty("state", e.state());
			j.addProperty("text", e.text());
			j.addProperty("at", Instant.ofEpochMilli(e.atMs()).toString());
			events.add(j);
		}
		o.add("events", events);
		return new Request("application/json", Map.of(), o.toString());
	}
}
