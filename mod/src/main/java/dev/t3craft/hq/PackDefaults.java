package dev.t3craft.hq;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import dev.t3craft.hq.pack.BlockStateText;
import dev.t3craft.hq.pack.HqLayoutPacks;
import dev.t3craft.hq.pack.LayoutParams;
import dev.t3craft.hq.plan.Box;
import dev.t3craft.hq.plan.BuildPlan;
import dev.t3craft.hq.plan.Palette;
import dev.t3craft.hq.style.HqStyle;
import dev.t3craft.hq.style.HqStyles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * feat/hq-layout-packs: the code's values a pack starts from, for the export and for resolving
 * {@code palette} keys. Public bridge into this package's {@link Palette} and styles.
 */
public final class PackDefaults {
	private PackDefaults() {
	}

	/** Every material field of {@link Palette} (block states and blocks), by field name; {@code air} left out. */
	public static Map<String, BlockState> paletteDefaults() {
		Map<String, BlockState> out = new TreeMap<>();
		Palette p = new Palette();
		for (Field f : Palette.class.getDeclaredFields()) {
			if (Modifier.isStatic(f.getModifiers()) || f.getName().equals("air")) {
				continue;
			}
			try {
				f.setAccessible(true);
				Object v = f.get(p);
				if (v instanceof BlockState s) {
					out.put(f.getName(), s);
				} else if (v instanceof Block b) {
					out.put(f.getName(), b.defaultBlockState());
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				// not a material
			}
		}
		return out;
	}

	/** The role materials of the style that draws {@code layoutId}. */
	public static Map<HqStyle.Role, BlockState> roleDefaults(String layoutId) {
		return HqStyles.forLayout(layoutId).palette();
	}

	/**
	 * The export of {@code layoutId}: every role (and, for classic, every {@link Palette} field) as its
	 * block id, every parameter at its default, empty {@code blocks} and {@code rooms}, plus the
	 * parameters' ranges and meaning under {@code _params}. Loading it changes nothing.
	 */
	public static String exportJson(String layoutId) {
		JsonObject o = new JsonObject();
		o.addProperty("layout", layoutId);
		o.addProperty("_doc", "docs/features/hq-layout-packs.md. Blocks are written without properties: each replaced block keeps its own "
				+ "facing/half/axis; properties you add here are forced on every block replaced. Remove a key to use the code's value.");
		JsonObject roles = new JsonObject();
		for (Map.Entry<HqStyle.Role, BlockState> e : roleDefaults(layoutId).entrySet()) {
			roles.addProperty(e.getKey().name(), id(e.getValue()));
		}
		o.add("roles", roles);
		if (layoutId.equals(HqLayouts.CLASSIC)) {
			JsonObject pal = new JsonObject();
			for (Map.Entry<String, BlockState> e : paletteDefaults().entrySet()) {
				pal.addProperty(e.getKey(), id(e.getValue()));
			}
			o.add("palette", pal);
		}
		o.add("blocks", new JsonObject());
		JsonObject params = new JsonObject();
		JsonObject docs = new JsonObject();
		for (LayoutParams.Spec s : LayoutParams.of(layoutId)) {
			params.addProperty(s.key(), s.toPack(s.def()));
			docs.addProperty(s.key(), String.format(Locale.ROOT, "%s [%s..%s, default %s]", s.doc(), s.toPack(s.min()), s.toPack(s.max()), s.toPack(s.def())));
		}
		o.add("params", params);
		o.add("_params", docs);
		o.add("rooms", new JsonObject());
		return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o) + "\n";
	}

	private static String id(BlockState s) {
		String f = BlockStateText.format(s);
		int i = f.indexOf('[');
		return i < 0 ? f : f.substring(0, i);
	}

	/**
	 * The modules a room may replace, per layout, for a sample HQ (three projects): id, group and box
	 * (minimum corner and size, plan coordinates). A room's structure is placed from the box's minimum
	 * corner; save it with the box's size.
	 */
	public static String moduleList() {
		StringBuilder sb = new StringBuilder("# Modules of every layout for a sample HQ (3 projects, 4 threads each).\n"
				+ "# rooms/<layout>/<module id>.nbt replaces that module's content; the structure's corner goes on the box's min corner.\n"
				+ "# id  group  min(x,y,z)  size(x,y,z)\n");
		HqInput sample = sample();
		for (HqLayoutInfo info : HqLayouts.all()) {
			sb.append("\n[").append(info.id()).append("]\n");
			try {
				HqStyle style = HqStyles.forLayout(info.id());
				BuildPlan plan = HqLayoutPacks.withPack(info.id(), "", () -> new HqGenerator(style).generate(style.allocate(sample, null)));
				if (plan == null) {
					continue;
				}
				for (BuildPlan.Module m : plan.modules()) {
					if (m.sparse()) {
						continue;
					}
					Box b = m.box();
					sb.append(String.format(Locale.ROOT, "%s  %s  %d,%d,%d  %dx%dx%d%n", m.id(), m.group(), b.minX(), b.minY(), b.minZ(), b.sizeX(), b.sizeY(),
							b.sizeZ()));
				}
			} catch (RuntimeException e) {
				sb.append("# failed: ").append(e).append('\n');
			}
		}
		return sb.toString();
	}

	private static HqInput sample() {
		String[][] projects = {{"vps", "t3craft"}, {"laptop", "forja"}, {"vps", "telar"}};
		HqInput.Builder b = HqInput.builder();
		for (String[] p : projects) {
			String key = p[0] + ":" + p[1];
			b.project(key, p[1], p[0], p[0]);
			for (int t = 0; t < 4; t++) {
				b.thread(key, p[1], p[0], p[0], "demo-" + p[1] + "-" + t, false);
			}
		}
		return b.build();
	}
}
