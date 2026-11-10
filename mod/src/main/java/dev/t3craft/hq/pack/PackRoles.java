package dev.t3craft.hq.pack;

import dev.t3craft.hq.PackDefaults;
import dev.t3craft.hq.style.HqStyle;
import java.util.ArrayList;
import java.util.List;

/** feat/hq-layout-packs: the names a pack's {@code roles} and {@code palette} sections accept. */
final class PackRoles {
	private PackRoles() {
	}

	static boolean known(String role) {
		for (HqStyle.Role r : HqStyle.Role.values()) {
			if (r.name().equals(role)) {
				return true;
			}
		}
		return false;
	}

	static List<String> names() {
		List<String> out = new ArrayList<>();
		for (HqStyle.Role r : HqStyle.Role.values()) {
			out.add(r.name());
		}
		return out;
	}

	static boolean knownPaletteField(String name) {
		return PackDefaults.paletteDefaults().containsKey(name);
	}

	static List<String> paletteFields() {
		return List.copyOf(PackDefaults.paletteDefaults().keySet());
	}
}
