package dev.t3craft.client.portable.held;

import dev.t3craft.client.portable.held.HeldScreenModel.Row;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Decides when the held screen's texture must be redrawn (pure, tested). The rows are value records,
 * so "the state changed" is plain equality: the texture is rasterised and uploaded only when
 * {@link #offer} returns true, never once per frame. {@link #version()} counts the redraws.
 */
public final class HeldScreenCache {
	private @Nullable List<Row> last;
	private int version;

	/** @return true when {@code rows} differ from the last accepted rows (caller redraws). */
	public boolean offer(List<Row> rows) {
		if (rows.equals(last)) {
			return false;
		}
		last = List.copyOf(rows);
		version++;
		return true;
	}

	/** Forget the last rows (texture released): the next {@link #offer} always redraws. */
	public void invalidate() {
		last = null;
	}

	public boolean hasContent() {
		return last != null;
	}

	public int version() {
		return version;
	}
}
