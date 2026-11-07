package dev.t3craft.hq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HqStyleTest {
	@BeforeAll
	static void boot() {
		HqTestSupport.boot();
	}

	/** Fingerprints of the generator before styles existed (b74a07b): classic must keep them byte for byte. */
	@Test
	void classicIsTheOriginalHqByteForByte() {
		String[][] golden = {
				{"0,0", "3fc757c00df4b3c18246a76a3038d93a01f9ac691b2f65cacee9fcef61e32dc9"},
				{"1,3", "2c7930d893331d1de78ec95d4a31fcb5cddb3122ee6221a8f8d89537dc6a9286"},
				{"2,8", "e288b7b486ad486ecd5f4c30e7726c8da9d2b5490ee4e907737d0039c535aec7"},
				{"4,7", "c374ee349927fbd085d38c02d947035d3a2f3de1e2954e2406f207707fa9975a"},
				{"3,13", "1f6834514f1117c7181e1762d7ef8696cde7b39c96f56fc8ec5f3de34bb95095"}};
		for (String[] g : golden) {
			String[] pt = g[0].split(",");
			HqInput in = HqTestSupport.input(Integer.parseInt(pt[0]), Integer.parseInt(pt[1]));
			if (pt[0].equals("4")) {
				in = HqTestSupport.settle(in, "env:p1-t2");
			}
			assertEquals(g[1], new HqGenerator(HqStyles.classic()).generate(HqLayout.plan(in)).fingerprint(), g[0]);
		}
	}
}
