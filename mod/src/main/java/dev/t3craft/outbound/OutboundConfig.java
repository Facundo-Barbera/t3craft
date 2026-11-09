package dev.t3craft.outbound;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.t3.Json;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The visible, non-secret outbound settings ({@code <config>/t3craft/outbound.json}): the master switch
 * (<b>off by default</b>: this sends data off the machine) and the destinations. No URL in here, ever:
 * a {@code url} key in the file is ignored (see {@link OutboundStore}).
 */
public record OutboundConfig(boolean enabled, List<Destination> destinations) {
	public static final int SCHEMA_VERSION = 1;
	public static final int MAX_DESTINATIONS = 8;
	public static final OutboundConfig OFF = new OutboundConfig(false, List.of());

	public OutboundConfig {
		destinations = List.copyOf(destinations);
	}

	public @Nullable Destination destination(String id) {
		for (Destination d : destinations) {
			if (d.id().equals(id)) {
				return d;
			}
		}
		return null;
	}

	public OutboundConfig withEnabled(boolean v) {
		return new OutboundConfig(v, destinations);
	}

	/** Replaces the destination with the same id, or appends it. */
	public OutboundConfig withDestination(Destination d) {
		List<Destination> out = new ArrayList<>();
		boolean replaced = false;
		for (Destination x : destinations) {
			if (x.id().equals(d.id())) {
				out.add(d);
				replaced = true;
			} else {
				out.add(x);
			}
		}
		if (!replaced) {
			out.add(d);
		}
		return new OutboundConfig(enabled, out);
	}

	public OutboundConfig withoutDestination(String id) {
		List<Destination> out = new ArrayList<>(destinations);
		out.removeIf(d -> d.id().equals(id));
		return new OutboundConfig(enabled, out);
	}

	/** Tolerant: anything unreadable reads as "off / no destinations". Unknown keys are ignored. */
	public static OutboundConfig fromJson(String json) {
		JsonElement root = JsonParser.parseString(json);
		if (!root.isJsonObject()) {
			return OFF;
		}
		JsonObject o = root.getAsJsonObject();
		boolean enabled = Json.bool(o, "enabled");
		List<Destination> out = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		JsonArray arr = Json.arr(o, "destinations");
		if (arr != null) {
			for (JsonElement el : arr) {
				if (!el.isJsonObject() || out.size() >= MAX_DESTINATIONS) {
					continue;
				}
				JsonObject d = el.getAsJsonObject();
				String id = Json.str(d, "id");
				if (id == null || !Destination.ID.matcher(id).matches() || !seen.add(id)) {
					continue;
				}
				Boolean destEnabled = Json.optBool(d, "enabled");
				Boolean pendingOnly = Json.optBool(d, "pendingOnly");
				EnumSet<EventType> events = EnumSet.noneOf(EventType.class);
				JsonArray ev = Json.arr(d, "events");
				if (ev != null) {
					for (JsonElement e : ev) {
						EventType t = e.isJsonPrimitive() ? EventType.fromWire(e.getAsString()) : null;
						if (t != null) {
							events.add(t);
						}
					}
				}
				out.add(new Destination(id, Json.str(d, "name"), Template.fromWire(Json.str(d, "template")),
						destEnabled == null || destEnabled, events,
						pendingOnly == null || pendingOnly, // missing = the private choice
						Json.str(d, "click")));
			}
		}
		return new OutboundConfig(enabled, out);
	}

	public String toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("schemaVersion", SCHEMA_VERSION);
		o.addProperty("enabled", enabled);
		JsonArray arr = new JsonArray();
		for (Destination d : destinations) {
			JsonObject j = new JsonObject();
			j.addProperty("id", d.id());
			j.addProperty("name", d.name());
			j.addProperty("template", d.template().wire);
			j.addProperty("enabled", d.enabled());
			JsonArray ev = new JsonArray();
			for (EventType t : d.events()) {
				ev.add(t.wire);
			}
			j.add("events", ev);
			j.addProperty("pendingOnly", d.pendingOnly());
			if (d.clickUrl() != null) {
				j.addProperty("click", d.clickUrl());
			}
			arr.add(j);
		}
		o.add("destinations", arr);
		return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o);
	}
}
