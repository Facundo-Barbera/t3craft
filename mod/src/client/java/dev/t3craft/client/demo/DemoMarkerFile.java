package dev.t3craft.client.demo;

import dev.t3craft.T3Craft;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.UnaryOperator;

/** Reads/writes {@link DemoMarker} in a world folder. Synchronized: server and client threads both touch it. */
final class DemoMarkerFile {
	private DemoMarkerFile() {
	}

	static synchronized DemoMarker read(Path worldRoot) {
		Path f = worldRoot.resolve(DemoMarker.FILE_NAME);
		try {
			return Files.exists(f) ? DemoMarker.parse(Files.readString(f, StandardCharsets.UTF_8)) : DemoMarker.EMPTY;
		} catch (IOException e) {
			T3Craft.LOGGER.warn("[demo] could not read {}", f, e);
			return DemoMarker.EMPTY;
		}
	}

	static synchronized DemoMarker update(Path worldRoot, UnaryOperator<DemoMarker> change) {
		DemoMarker next = change.apply(read(worldRoot));
		Path f = worldRoot.resolve(DemoMarker.FILE_NAME);
		Path tmp = worldRoot.resolve(DemoMarker.FILE_NAME + ".tmp");
		try {
			Files.writeString(tmp, next.toJson(), StandardCharsets.UTF_8);
			try {
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			T3Craft.LOGGER.warn("[demo] could not write {}", f, e);
		}
		return next;
	}
}
