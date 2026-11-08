package dev.t3craft.client.chat.compose;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Unsent text per thread ({@code env:threadId}), like T3 Code's composer draft store
 * ({@code apps/web/src/composerDraftStore.ts}): switching threads keeps what you were writing.
 * In memory for the session (not persisted), at most {@value #MAX} threads, least recently used
 * dropped first. Empty drafts are not kept.
 */
public final class ComposerDrafts {
	static final int MAX = 64;

	public record Draft(String text, int caret) {
	}

	private final LinkedHashMap<String, Draft> drafts = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Draft> e) {
			return size() > MAX;
		}
	};

	public void save(String key, String text, int caret) {
		if (text.isEmpty()) {
			drafts.remove(key);
		} else {
			drafts.put(key, new Draft(text, caret));
		}
	}

	public @Nullable Draft get(String key) {
		return drafts.get(key);
	}

	public void remove(String key) {
		drafts.remove(key);
	}

	public int size() {
		return drafts.size();
	}
}
