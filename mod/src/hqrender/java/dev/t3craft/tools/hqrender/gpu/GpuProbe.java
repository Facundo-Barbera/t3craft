package dev.t3craft.tools.hqrender.gpu;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * {@code --gpu-probe}: finds out, with child processes (a native crash cannot touch the daemon), which GPU routes the
 * render PC offers: the CUDA driver API (PTX compiled by the driver), and surfaceless EGL + OpenGL compute shaders on
 * a few platforms. The text goes to the log (so into report.txt) and to {@code gpu-probe.txt}.
 */
public final class GpuProbe {
	private GpuProbe() {
	}

	private static final String SYSTEM_SCRIPT = String.join("\n",
			"echo '--- uname'; uname -a",
			"echo '--- /usr/lib/wsl/lib'; ls -l /usr/lib/wsl/lib 2>&1 | head -60",
			"echo '--- /dev'; ls -l /dev/dxg /dev/dri 2>&1 | head",
			"echo '--- libs in /usr/lib/x86_64-linux-gnu'; ls /usr/lib/x86_64-linux-gnu 2>/dev/null | grep -i -E 'libEGL|libGL|libOpenCL|libnvrtc|libcuda|nvidia|libvulkan|libgbm|libglapi|libOpenGL|d3d12|dxcore' | head -60",
			"echo '--- dri drivers'; ls /usr/lib/x86_64-linux-gnu/dri 2>&1 | head -40",
			"echo '--- glvnd egl vendors'; ls /usr/share/glvnd/egl_vendor.d 2>&1",
			"echo '--- env'; env | grep -i -E 'DISPLAY|WAYLAND|LD_LIBRARY|MESA|GALLIUM|LIBGL|CUDA|WSL' | sort",
			"echo '--- nvidia-smi'; /usr/lib/wsl/lib/nvidia-smi 2>&1 | head -20",
			"echo '--- ldconfig'; ldconfig -p 2>/dev/null | grep -i -E 'cuda|nvrtc|nvidia|libEGL|libGL\\.|d3d12|dxcore|nvoptix|OpenCL' | head -40",
			"echo '--- compilers anywhere'; timeout 40 find / -xdev \\( -name 'libnvrtc*' -o -name 'ptxas' -o -name 'nvcc' -o -name 'libnvidia-ptxjitcompiler*' \\) 2>/dev/null | head",
			"echo '--- mesa version'; dpkg -l 2>/dev/null | grep -i -E 'mesa|libegl|libgl1|libgles' | awk '{print $2, $3}' | head -20");

	private record Step(String title, List<String> args, Map<String, String> env) {
	}

	public static void run(Path out) throws Exception {
		Files.createDirectories(out);
		StringBuilder sb = new StringBuilder();
		sb.append("gpu probe\n");
		sb.append("java ").append(System.getProperty("java.version")).append("  home ").append(System.getProperty("java.home")).append('\n');
		sb.append("os ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append('\n');
		sb.append("child classes: ").append(Launcher.classesRoot()).append("\n\n");

		sb.append("=== system ===\n");
		Process sys = new ProcessBuilder("bash", "-c", SYSTEM_SCRIPT).redirectErrorStream(true).start();
		sys.getOutputStream().close();
		byte[] text = sys.getInputStream().readAllBytes();
		sys.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
		sb.append(new String(text, java.nio.charset.StandardCharsets.UTF_8)).append('\n');

		Map<String, String> none = Map.of();
		List<Step> steps = List.of(
				new Step("CUDA driver API (libcuda, PTX JIT)", List.of("probe-cuda"), none),
				new Step("EGL surfaceless + OpenGL compute", List.of("probe-egl", "surfaceless"), none),
				new Step("EGL surfaceless, GALLIUM_DRIVER=d3d12", List.of("probe-egl", "surfaceless"), Map.of("GALLIUM_DRIVER", "d3d12")),
				new Step("EGL device 0", List.of("probe-egl", "device:0"), none),
				new Step("EGL device 1", List.of("probe-egl", "device:1"), none),
				new Step("EGL default display", List.of("probe-egl", "default"), none));
		for (Step s : steps) {
			sb.append("=== ").append(s.title()).append(" ===\n");
			long t0 = System.nanoTime();
			try {
				Launcher.Result r = Launcher.run(s.args(), s.env(), 90);
				sb.append(r.output());
				sb.append("[exit ").append(r.exit()).append(r.timedOut() ? ", TIMED OUT" : "").append(", ").append((System.nanoTime() - t0) / 1_000_000)
						.append(" ms]\n\n");
			} catch (Exception e) {
				sb.append("could not run the child: ").append(e).append("\n\n");
			}
		}
		Files.writeString(out.resolve("gpu-probe.txt"), sb.toString());
		System.out.println(sb);
		// the job protocol wants an image; a small swatch
		BufferedImage img = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < 48; y++) {
			for (int x = 0; x < 48; x++) {
				img.setRGB(x, y, (x * 5) << 16 | (y * 5) << 8 | 0x80);
			}
		}
		ImageIO.write(img, "png", out.resolve("gpu_probe.png").toFile());
	}
}
