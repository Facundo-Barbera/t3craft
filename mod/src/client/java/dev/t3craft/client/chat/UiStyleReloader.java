package dev.t3craft.client.chat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reload listener of {@code assets/t3craft/gui/ui-style.json} (issue #12): F3+T (and every resource
 * reload) re-reads the UI tokens into {@link UiStyle}. Fabric API only, no extra mod. The file is read
 * off-thread ({@link #prepare}) and applied on the game thread ({@link #apply}).
 */
public final class UiStyleReloader extends SimpleReloadListener<UiStyleReloader.Doc> {
	private static final Logger LOG = LoggerFactory.getLogger("t3craft/ui-style");
	private static final Identifier ID = Identifier.fromNamespaceAndPath("t3craft", "ui_style");
	private static final Identifier FILE = Identifier.fromNamespaceAndPath("t3craft", UiStyle.PATH);

	/** The file's text, or null when no pack has it (or it could not be read). */
	public record Doc(@Nullable String json) {
	}

	/** Call once from the client entrypoint. */
	public static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ID, new UiStyleReloader());
	}

	@Override
	public Doc prepare(PreparableReloadListener.SharedState state) {
		Optional<Resource> res = state.resourceManager().getResource(FILE);
		if (res.isEmpty()) {
			return new Doc(null);
		}
		try (BufferedReader in = res.get().openAsReader()) {
			return new Doc(in.lines().collect(Collectors.joining("\n")));
		} catch (IOException | RuntimeException e) {
			LOG.warn("cannot read {}: {}", FILE, e.toString());
			return new Doc(null);
		}
	}

	@Override
	public void apply(Doc doc, PreparableReloadListener.SharedState state) {
		if (doc.json() == null) {
			UiStyle.reloadBuiltin();
			return;
		}
		UiStyle.reload(new StringReader(doc.json()));
	}
}
