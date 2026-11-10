package dev.t3craft.piece;

import dev.t3craft.hq.layout.HqLayoutInfo;
import dev.t3craft.hq.layout.HqLayouts;
import dev.t3craft.hq.pack.HqLayoutPacks;
import dev.t3craft.hq.pack.LayoutPack;
import dev.t3craft.hq.pack.RoomTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * feat/piece-catalog-preview: a room of a layout pack ({@code config/t3craft/hq-layouts/rooms/<layout>/<module>.nbt},
 * see docs/features/hq-layout-packs.md), shown alone as saved: not rotated, not offset into its module. The
 * list follows the packs that are active now; the room is looked up again at every build, so a pack reloaded
 * in the meantime is what shows.
 */
final class PackPiece implements Piece {
	private final String layoutId;
	private final String moduleId;

	private PackPiece(String layoutId, String moduleId) {
		this.layoutId = layoutId;
		this.moduleId = moduleId;
	}

	/** One piece per room of every active pack. */
	static List<Piece> current() {
		List<Piece> out = new ArrayList<>();
		for (HqLayoutInfo info : HqLayouts.all()) {
			LayoutPack pack = HqLayoutPacks.pack(info.id());
			if (pack == null) {
				continue;
			}
			for (String module : pack.rooms().keySet()) {
				out.add(new PackPiece(info.id(), module));
			}
		}
		return out;
	}

	@Override
	public String id() {
		return ("pack_" + layoutId + "_" + moduleId).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.+-]", "_");
	}

	@Override
	public String group() {
		return "pack";
	}

	@Override
	public String title() {
		return "Pack room " + moduleId + " (" + layoutId + ")";
	}

	@Override
	public String note() {
		return "room of the layout pack of " + layoutId + " replacing module " + moduleId + " (config/t3craft/hq-layouts)";
	}

	@Override
	public Built build(PieceSpec spec, boolean fresh) {
		LayoutPack pack = HqLayoutPacks.pack(layoutId);
		LayoutPack.Room room = pack == null ? null : pack.rooms().get(moduleId);
		if (room == null) {
			throw new IllegalStateException("the pack of " + layoutId + " no longer has a room for " + moduleId);
		}
		RoomTemplate t = room.template();
		PieceModel.Builder b = PieceModel.builder();
		for (RoomTemplate.Cell c : t.cells()) {
			if (!c.state().isAir()) {
				b.add(c.pos().getX(), c.pos().getY(), c.pos().getZ(), c.state(), c.be());
			}
		}
		return new Built(b.build(), "room file " + t.file() + ", " + t.sizeX() + "x" + t.sizeY() + "x" + t.sizeZ() + ", as saved; in the HQ it is turned "
				+ room.rotation() + " and offset " + room.offset().toShortString());
	}
}
