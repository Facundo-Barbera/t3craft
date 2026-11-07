package dev.t3craft.t3;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Every operate kind and UI string is translated in both shipped languages. */
class OperateLangTest {
	static JsonObject lang(String name) throws Exception {
		try (var in = OperateLangTest.class.getResourceAsStream("/assets/t3craft/lang/" + name + ".json")) {
			assertNotNull(in, name);
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	@Test
	void kindsAndScreensAreTranslated() throws Exception {
		for (String l : new String[] {"en_us", "es_es"}) {
			JsonObject o = lang(l);
			for (OperateKind k : OperateKind.values()) {
				assertTrue(o.has(k.langKey()), l + " misses " + k.langKey());
			}
			for (String key : new String[] {"t3craft.operate.requires", "t3craft.operate.confirm.title", "t3craft.operate.enable.warning",
					"t3craft.operate.revoke.button"}) {
				assertTrue(o.has(key), l + " misses " + key);
			}
		}
		assertEquals("Requiere control (operate)", lang("es_es").get("t3craft.operate.requires").getAsString());
	}
}
