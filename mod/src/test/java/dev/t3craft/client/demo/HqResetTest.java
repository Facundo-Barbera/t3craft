package dev.t3craft.client.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.t3craft.client.demo.HqReset.Action;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HqResetTest {
	private static final String HQ = DemoWorldSpec.DEFAULT_FOLDER;
	private static final LocalDateTime T = LocalDateTime.of(2026, 11, 7, 14, 3, 9);

	@TempDir
	Path tmp;
	Path saves;

	@BeforeEach
	void setUp() throws IOException {
		saves = Files.createDirectories(tmp.resolve("saves"));
	}

	private Path hqWorld() throws IOException {
		Path w = Files.createDirectories(saves.resolve(HQ));
		Files.writeString(w.resolve("level.dat"), "lvl");
		Files.createDirectories(w.resolve("region"));
		Files.writeString(w.resolve("region/r.0.0.mca"), "chunks");
		Files.writeString(w.resolve(HqReset.SESSION_LOCK), "lock");
		Files.writeString(w.resolve(DemoMarker.FILE_NAME), "{\"setup\":1,\"structure\":\"hq\",\"version\":1}");
		return w;
	}

	// ---- decision ----------------------------------------------------------------------------

	@ParameterizedTest
	@CsvSource({
			// inWorld, inDemo, exists, confirmed, expected
			"true, false, true, false, REFUSE_OTHER_WORLD",
			"true, false, true, true, REFUSE_OTHER_WORLD",
			"true, false, false, true, REFUSE_OTHER_WORLD",
			"true, true, true, false, CONFIRM",
			"true, true, true, true, RESET",
			"false, false, true, false, CONFIRM",
			"false, false, true, true, RESET",
			"false, false, false, false, NOTHING_TO_RESET",
			"false, false, false, true, NOTHING_TO_RESET",
	})
	void decide(boolean inWorld, boolean inDemo, boolean exists, boolean confirmed, Action expected) {
		assertEquals(expected, HqReset.decide(inWorld, inDemo, exists, confirmed));
	}

	// ---- validation --------------------------------------------------------------------------

	@Test
	void validDemoFolderPasses() throws IOException {
		Path w = hqWorld();
		HqReset.Check c = HqReset.validate(saves, HQ);
		assertTrue(c.ok(), c.reason());
		assertEquals(w.toRealPath(), c.target());
	}

	@Test
	void markerIsOptional() throws IOException {
		Files.createDirectories(saves.resolve(HQ));
		assertTrue(HqReset.validate(saves, HQ).ok());
	}

	@Test
	void foreignMarkerRefused() throws IOException {
		Path w = Files.createDirectories(saves.resolve(HQ));
		Files.writeString(w.resolve(DemoMarker.FILE_NAME), "not json at all");
		assertFalse(HqReset.validate(saves, HQ).ok());
		Files.delete(w.resolve(DemoMarker.FILE_NAME));
		Files.createDirectories(w.resolve(DemoMarker.FILE_NAME));
		assertFalse(HqReset.validate(saves, HQ).ok());
	}

	@Test
	void missingFolderRefused() {
		assertFalse(HqReset.validate(saves, HQ).ok());
		assertFalse(HqReset.validate(tmp.resolve("nope"), HQ).ok());
	}

	@Test
	void fileInsteadOfFolderRefused() throws IOException {
		Files.writeString(saves.resolve(HQ), "x");
		assertFalse(HqReset.validate(saves, HQ).ok());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "..", ".", "../saves", "t3craft-hq/..", "t3craft-hq/region", "/tmp", "..\\x", "a/b",
			"t3craft hq", "t3craft-hq.", "*", "t3craft-*"})
	void weirdFolderIdsRefused(String id) throws IOException {
		hqWorld();
		assertFalse(HqReset.validate(saves, id).ok(), id);
	}

	@Test
	void otherWorldNeverMatches() throws IOException {
		hqWorld();
		Files.createDirectories(saves.resolve("My World"));
		Files.createDirectories(saves.resolve("t3craft-hq-copy"));
		assertFalse(HqReset.validate(saves, "My World").ok());
		assertEquals(saves.resolve(HQ).toRealPath(), HqReset.validate(saves, HQ).target());
	}

	@Test
	void caseMustMatchExactly() throws IOException {
		Files.createDirectories(saves.resolve("T3Craft-HQ"));
		boolean caseInsensitiveFs = Files.exists(saves.resolve(HQ));
		// On a case-sensitive FS the lowercase folder does not exist; on an insensitive one the real
		// name differs in case. Either way: refused.
		assertFalse(HqReset.validate(saves, HQ).ok(), "caseInsensitiveFs=" + caseInsensitiveFs);
	}

	@Test
	void symlinkedDemoFolderRefused() throws IOException {
		Path elsewhere = Files.createDirectories(tmp.resolve("precious"));
		Files.writeString(elsewhere.resolve("keep.txt"), "keep");
		try {
			Files.createSymbolicLink(saves.resolve(HQ), elsewhere);
		} catch (UnsupportedOperationException | IOException e) {
			assumeTrue(false, "symlinks not supported here");
		}
		assertFalse(HqReset.validate(saves, HQ).ok());
		assertThrows(IOException.class, () -> HqReset.delete(saves, HQ));
		assertTrue(Files.exists(elsewhere.resolve("keep.txt")));
	}

	@Test
	void symlinkedSavesDirIsResolved() throws IOException {
		Path w = hqWorld();
		Path link = tmp.resolve("saves-link");
		try {
			Files.createSymbolicLink(link, saves);
		} catch (UnsupportedOperationException | IOException e) {
			assumeTrue(false, "symlinks not supported here");
		}
		HqReset.Check c = HqReset.validate(link, HQ);
		assertTrue(c.ok(), c.reason());
		assertEquals(w.toRealPath(), c.target());
	}

	@Test
	void savesPathWithDotDotStillTargetsExactChild() throws IOException {
		Path w = hqWorld();
		Path odd = tmp.resolve("saves").resolve("..").resolve("saves");
		assertEquals(w.toRealPath(), HqReset.validate(odd, HQ).target());
	}

	// ---- delete ------------------------------------------------------------------------------

	@Test
	void deleteRemovesOnlyTheDemoFolder() throws IOException {
		hqWorld();
		Path other = Files.createDirectories(saves.resolve("My World"));
		Files.writeString(other.resolve("level.dat"), "mine");
		Path sibling = Files.createDirectories(saves.resolve("t3craft-hq-old"));
		Path outside = Files.createDirectories(tmp.resolve("precious"));
		Files.writeString(outside.resolve("keep.txt"), "keep");
		try {
			Files.createSymbolicLink(saves.resolve(HQ).resolve("link-out"), outside);
		} catch (UnsupportedOperationException | IOException ignored) {
			// fine: just no inner symlink in this run
		}
		HqReset.delete(saves, HQ);
		assertFalse(Files.exists(saves.resolve(HQ)));
		assertTrue(Files.exists(other.resolve("level.dat")));
		assertTrue(Files.isDirectory(sibling));
		assertTrue(Files.exists(outside.resolve("keep.txt")), "inner symlink must not be followed");
	}

	@Test
	void deleteRefusesInvalidTarget() throws IOException {
		Files.createDirectories(saves.resolve("My World"));
		assertThrows(IOException.class, () -> HqReset.delete(saves, "My World/.."));
		assertThrows(IOException.class, () -> HqReset.delete(saves, HQ));
		assertTrue(Files.isDirectory(saves.resolve("My World")));
	}

	// ---- backup ------------------------------------------------------------------------------

	@Test
	void backupNameHasFolderAndTimestamp() {
		assertEquals("t3craft-hq_2026-11-07_14-03-09_reset.zip", HqReset.backupName(HQ, T));
		assertNotEquals(HqReset.backupName(HQ, T), HqReset.backupName(HQ, T.plusSeconds(1)));
	}

	@Test
	void backupFileAvoidsCollisions() throws IOException {
		Path dir = Files.createDirectories(tmp.resolve("backups"));
		Path first = HqReset.backupFile(dir, HQ, T);
		Files.writeString(first, "x");
		Path second = HqReset.backupFile(dir, HQ, T);
		assertEquals("t3craft-hq_2026-11-07_14-03-09_reset-2.zip", second.getFileName().toString());
	}

	@Test
	void zipContainsWorldButNotSessionLock() throws IOException {
		Path w = hqWorld();
		Path zip = HqReset.zip(w, tmp.resolve("backups"), HQ, T);
		assertEquals(tmp.resolve("backups").resolve(HqReset.backupName(HQ, T)), zip);
		List<String> names = new ArrayList<>();
		try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
			for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
				names.add(e.getName());
				if (e.getName().endsWith("level.dat")) {
					assertEquals("lvl", new String(in.readAllBytes(), StandardCharsets.UTF_8));
				}
			}
		}
		assertTrue(names.contains(HQ + "/level.dat"), names.toString());
		assertTrue(names.contains(HQ + "/region/r.0.0.mca"), names.toString());
		assertTrue(names.contains(HQ + "/" + DemoMarker.FILE_NAME), names.toString());
		assertFalse(names.stream().anyMatch(n -> n.endsWith(HqReset.SESSION_LOCK)));
		assertTrue(Files.exists(w.resolve("level.dat")), "zip never deletes");
	}

	@Test
	void backupFlagDefaultsOnAndIsRespected() {
		assertTrue(HqReset.shouldBackup(DemoConfig.defaults()));
		assertTrue(HqReset.shouldBackup(DemoConfig.fromRoot(null)));
		assertTrue(HqReset.shouldBackup(cfg("{\"demo\":{}}")));
		assertTrue(HqReset.shouldBackup(cfg("{\"demo\":{\"resetBackup\":true}}")));
		assertFalse(HqReset.shouldBackup(cfg("{\"demo\":{\"resetBackup\":false}}")));
		DemoConfig bad = cfg("{\"demo\":{\"resetBackup\":\"no\"}}");
		assertTrue(HqReset.shouldBackup(bad));
		assertTrue(bad.warnings().contains("demo.resetBackup"));
		assertFalse(HqReset.shouldBackup(DemoConfig.defaults().withResetBackup(false)));
	}

	private static DemoConfig cfg(String json) {
		JsonObject o = JsonParser.parseString(json).getAsJsonObject();
		return DemoConfig.fromRoot(o);
	}
}
