package dev.t3craft.client.chat;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Links in chat messages (feat/chat-links): what a {@link Markdown.Span#url()} points at and how to
 * read a T3 thread link. Pure Java.
 *
 * <ul>
 *   <li>{@link Kind#THREAD}: {@code t3-thread://v2/<envId>/<threadId>} (ids percent-encoded, e.g.
 *       {@code %3A}); opens the thread in the T3 screen.</li>
 *   <li>{@link Kind#WEB}: {@code http(s)://} and {@code mailto:}; the browser, after the vanilla
 *       confirmation.</li>
 *   <li>{@link Kind#FILE}: an absolute path found in the text ({@link #FILE_PREFIX} + path, maybe with
 *       {@code :line[:col]}) or a {@code file://} link; the path is copied, never opened (the game
 *       runs on another machine than the files).</li>
 * </ul>
 */
public final class Links {
	/** Prefix of the url the parser gives to a bare file path. */
	public static final String FILE_PREFIX = "file:";
	public static final String THREAD_SCHEME = "t3-thread://";

	public enum Kind {
		THREAD, WEB, FILE, OTHER
	}

	/** A thread link: environment id as the link names it (may be empty) and the decoded thread id. */
	public record ThreadRef(String envId, String threadId) {
	}

	private Links() {
	}

	public static Kind kind(@Nullable String url) {
		if (url == null) {
			return Kind.OTHER;
		}
		String u = url.toLowerCase(Locale.ROOT);
		if (u.startsWith(THREAD_SCHEME)) {
			return Kind.THREAD;
		}
		if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("mailto:")) {
			return Kind.WEB;
		}
		if (u.startsWith(FILE_PREFIX)) {
			return Kind.FILE;
		}
		return Kind.OTHER;
	}

	/**
	 * {@code t3-thread://v2/<env>/<thread>} (also {@code v1}, or {@code <env>/<thread>} and a bare
	 * {@code <thread>}), ids decoded; null when it is not a thread link or has no thread id. A query or
	 * fragment is ignored.
	 */
	public static @Nullable ThreadRef thread(@Nullable String url) {
		if (kind(url) != Kind.THREAD) {
			return null;
		}
		String rest = url.substring(THREAD_SCHEME.length());
		int cut = indexOfAny(rest, "?#");
		if (cut >= 0) {
			rest = rest.substring(0, cut);
		}
		while (rest.endsWith("/")) {
			rest = rest.substring(0, rest.length() - 1);
		}
		String[] parts = rest.split("/", -1);
		int k = 0;
		if (parts.length > 2 && parts[0].matches("v\\d+")) {
			k = 1;
		}
		String env;
		String thread;
		if (parts.length - k >= 2) {
			env = parts[k];
			thread = String.join("/", java.util.Arrays.copyOfRange(parts, k + 1, parts.length));
		} else {
			env = "";
			thread = parts[parts.length - 1];
		}
		thread = decode(thread);
		if (thread.isBlank()) {
			return null;
		}
		return new ThreadRef(decode(env), thread);
	}

	/** The path a file link copies: {@link #FILE_PREFIX} or {@code file://} removed, decoded for {@code file://}. */
	public static String filePath(String url) {
		if (url.regionMatches(true, 0, "file://", 0, 7)) {
			return decode(url.substring(7));
		}
		return url.regionMatches(true, 0, FILE_PREFIX, 0, FILE_PREFIX.length()) ? url.substring(FILE_PREFIX.length()) : url;
	}

	/** Percent-decoding that keeps {@code +} and never throws. */
	public static String decode(String s) {
		if (s.indexOf('%') < 0) {
			return s;
		}
		try {
			return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return s;
		}
	}

	/** Short form of a thread id for "thread 1a2b3c4d": the part after the last ':', at most 8 chars. */
	public static String shortId(String threadId) {
		String s = threadId;
		int colon = s.lastIndexOf(':');
		if (colon >= 0 && colon < s.length() - 1) {
			s = s.substring(colon + 1);
		}
		return s.length() <= 8 ? s : s.substring(0, 8);
	}

	/**
	 * The row of a thread link: same environment and thread first, then the thread in any environment
	 * (the link names T3's environment id, which need not be this mod's slug for it). Null when unknown.
	 */
	public static @Nullable ThreadRow find(List<ThreadRow> rows, ThreadRef ref) {
		ThreadRow any = null;
		for (ThreadRow r : rows) {
			String id = r.threadId();
			if (id == null || !(id.equals(ref.threadId()) || decode(id).equals(ref.threadId()))) {
				continue;
			}
			if (r.env().equals(ref.envId())) {
				return r;
			}
			if (any == null) {
				any = r;
			}
		}
		return any;
	}

	private static int indexOfAny(String s, String chars) {
		for (int i = 0; i < s.length(); i++) {
			if (chars.indexOf(s.charAt(i)) >= 0) {
				return i;
			}
		}
		return -1;
	}
}
