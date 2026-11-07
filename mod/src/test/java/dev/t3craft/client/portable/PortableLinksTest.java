package dev.t3craft.client.portable;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortableLinksTest {
	/** Stand-in with feat/chat's expected shape: {@code public static void open(String envId, String threadId)}. */
	public static final class FakeChatScreens {
		static final List<String> OPENED = new ArrayList<>();

		public static void open(String envId, String threadId) {
			OPENED.add(envId + "/" + threadId);
		}
	}

	/** Wrong shape (instance method): must be ignored. */
	public static final class NotStatic {
		public void open(String envId, String threadId) {
		}
	}

	@Test
	void discoversStaticOpenByNameAndCallsIt() {
		var loader = getClass().getClassLoader();
		PortableLinks.ChatOpener o = PortableLinks.findChat(loader, "does.not.Exist", NotStatic.class.getName(), FakeChatScreens.class.getName());
		assertNotNull(o);
		o.open("vps", "thread-1");
		assertEquals(List.of("vps/thread-1"), FakeChatScreens.OPENED);
	}

	@Test
	void nothingFoundMeansFallback() {
		assertNull(PortableLinks.findChat(getClass().getClassLoader(), "does.not.Exist", NotStatic.class.getName()));
	}

	@Test
	void candidateNamesFollowTheChatBranchName() {
		for (String name : PortableLinks.CHAT_CANDIDATES) {
			assertTrue(name.endsWith(".ChatScreens"), name);
		}
	}
}
