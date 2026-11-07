package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectSearchTest {
	@Test
	void noEnvironmentsGivesAnEmptyOutcomeNotAnError(@TempDir Path dir) throws Exception {
		DirectController d = new DirectController(new Environments(dir), () -> {
		});
		var o = d.search(" hello ", 10, 1_000).get(5, TimeUnit.SECONDS);
		assertTrue(o.noEnvironmentConnected());
		assertEquals(0, o.paired());
		assertEquals("hello", o.query());
		assertTrue(o.hits().isEmpty());
		assertTrue(o.errors().isEmpty());
	}

	@Test
	void searchesEveryConnectedEnvironmentAndMergesWithErrors(@TempDir Path dir) throws Exception {
		try (FakeT3Server vps = new FakeT3Server(); FakeT3Server mac = new FakeT3Server()) {
			vps.script = T3ConnectionSearchTest.script(req -> List.of(T3ConnectionSearchTest.success(req.get("id").getAsString(),
					"{\"matches\":[{\"threadId\":\"a\",\"quality\":0.8,\"snippet\":\"vps hit\"}]}")));
			mac.script = T3ConnectionSearchTest.script(req -> List.of("{\"_tag\":\"Exit\",\"requestId\":\"" + req.get("id").getAsString()
					+ "\",\"exit\":{\"_tag\":\"Failure\",\"cause\":[{\"_tag\":\"Fail\",\"error\":{\"_tag\":\"OrchestrationSearchThreadsError\",\"message\":\"busy\"}}]}}"));
			Environments envs = new Environments(dir);
			var session = new Pairing.Session(FakeT3Server.TOKEN, Set.of(Pairing.READ_SCOPE), System.currentTimeMillis() + 3_600_000);
			envs.save(new Environments.Environment("vps", "VPS", vps.base()), session);
			envs.save(new Environments.Environment("mac", "Mac", mac.base()), session);
			DirectController d = new DirectController(envs, () -> {
			});
			d.start();
			try {
				long deadline = System.currentTimeMillis() + 10_000;
				while (d.statuses().stream().anyMatch(s -> s.status() != T3Connection.Status.OK)) {
					assertTrue(System.currentTimeMillis() < deadline, "environments did not sync: " + d.statuses());
					Thread.sleep(20);
				}
				var o = d.search("thread", 10, 5_000).get(10, TimeUnit.SECONDS);
				assertEquals(2, o.searched());
				assertEquals(2, o.paired());
				// VPS: server hit "a" (boosted by the title "Thread a") ; Mac failed but VPS still counts
				assertEquals(List.of("VPS/a"), o.hits().stream().map(h -> h.envLabel() + "/" + h.threadId()).toList());
				assertEquals("vps hit", o.hits().getFirst().snippet());
				assertEquals(1, o.errors().size());
				assertEquals("Mac", o.errors().getFirst().envLabel());
				assertTrue(o.errors().getFirst().message().contains("busy"));
			} finally {
				d.stop();
			}
		}
	}

	@Test
	void describeUnwrapsAndSanitizes() {
		assertEquals("timed out after 8 s", DirectController.describe(new CompletionException(new ThreadSearch.SearchException("timed out after 8 s"))));
		assertEquals("cancelled", DirectController.describe(new CancellationException()));
		assertFalse(DirectController.describe(new RuntimeException("Bearer abc123")).contains("abc123"));
	}
}
