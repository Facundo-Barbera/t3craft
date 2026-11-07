package dev.t3craft.t3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Minimal fake T3 for tests only: binds 127.0.0.1 on an ephemeral port, closed after each test.
 * Serves POST /api/auth/websocket-ticket/rpc, POST /oauth/token and the /ws upgrade with plain
 * RFC 6455 text frames. {@code script} decides what to send for each client frame.
 */
final class FakeT3Server implements AutoCloseable {
	static final String TOKEN = "test-bearer";
	static final String TICKET = "test-ticket+/=";

	final ServerSocket server;
	final List<String> received = new CopyOnWriteArrayList<>();
	final List<String> requests = new CopyOnWriteArrayList<>();
	final BlockingQueue<String> inbox = new LinkedBlockingQueue<>();
	volatile Map<String, String> lastWsQuery = Map.of();
	volatile String lastTokenForm = "";
	volatile int ticketStatus = 200;
	volatile String tokenResponse = "{\"access_token\":\"issued\",\"issued_token_type\":\"urn:ietf:params:oauth:token-type:access_token\",\"token_type\":\"Bearer\",\"expires_in\":2592000,\"scope\":\"orchestration:read\"}";
	volatile Function<String, List<String>> script = msg -> List.of();
	private volatile OutputStream wsOut;

	FakeT3Server() throws IOException {
		server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		Thread t = new Thread(this::acceptLoop, "fake-t3");
		t.setDaemon(true);
		t.start();
	}

	URI base() {
		return URI.create("http://127.0.0.1:" + server.getLocalPort());
	}

	String nextFrame() throws InterruptedException {
		String s = inbox.poll(10, TimeUnit.SECONDS);
		if (s == null) {
			throw new AssertionError("no frame from client within 10 s; got " + received);
		}
		return s;
	}

	void send(String text) throws IOException {
		OutputStream out = wsOut;
		byte[] b = text.getBytes(StandardCharsets.UTF_8);
		synchronized (this) {
			out.write(0x81);
			if (b.length < 126) {
				out.write(b.length);
			} else if (b.length < 65536) {
				out.write(126);
				out.write(b.length >>> 8);
				out.write(b.length & 0xFF);
			} else {
				out.write(127);
				for (int i = 7; i >= 0; i--) {
					out.write((int) ((long) b.length >>> (8 * i)) & 0xFF);
				}
			}
			out.write(b);
			out.flush();
		}
	}

	private void acceptLoop() {
		while (!server.isClosed()) {
			try {
				Socket s = server.accept();
				Thread t = new Thread(() -> handle(s), "fake-t3-conn");
				t.setDaemon(true);
				t.start();
			} catch (IOException e) {
				return;
			}
		}
	}

	private static String readLine(InputStream in) throws IOException {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		int c;
		while ((c = in.read()) != -1) {
			if (c == '\n') {
				break;
			}
			if (c != '\r') {
				b.write(c);
			}
		}
		return b.toString(StandardCharsets.UTF_8);
	}

	private void handle(Socket s) {
		try (s) {
			InputStream in = s.getInputStream();
			OutputStream out = s.getOutputStream();
			String requestLine = readLine(in);
			Map<String, String> headers = new LinkedHashMap<>();
			String line;
			while (!(line = readLine(in)).isEmpty()) {
				int c = line.indexOf(':');
				headers.put(line.substring(0, c).trim().toLowerCase(), line.substring(c + 1).trim());
			}
			String[] parts = requestLine.split(" ");
			URI uri = URI.create(parts[1]);
			requests.add(parts[0] + " " + uri.getPath());
			int len = Integer.parseInt(headers.getOrDefault("content-length", "0"));
			String body = new String(in.readNBytes(len), StandardCharsets.UTF_8);
			switch (uri.getPath()) {
				case "/api/auth/websocket-ticket/rpc" -> {
					if (!("Bearer " + TOKEN).equals(headers.get("authorization"))) {
						respond(out, 401, "{\"code\":\"invalid_credential\"}");
					} else if (ticketStatus != 200) {
						respond(out, ticketStatus, "{}");
					} else {
						respond(out, 200, "{\"ticket\":\"" + TICKET + "\",\"expiresAt\":\"2026-11-07T12:05:00Z\"}");
					}
				}
				case "/oauth/token" -> {
					lastTokenForm = URLDecoder.decode(body, StandardCharsets.UTF_8);
					respond(out, 200, tokenResponse);
				}
				case "/ws" -> {
					Map<String, String> q = new LinkedHashMap<>();
					for (String kv : uri.getRawQuery().split("&")) {
						int e = kv.indexOf('=');
						q.put(kv.substring(0, e), URLDecoder.decode(kv.substring(e + 1), StandardCharsets.UTF_8));
					}
					lastWsQuery = q;
					if (!"2".equals(q.get("orchestrationProtocol"))) {
						respond(out, 426, "{\"code\":\"orchestration_protocol_incompatible\"}");
						return;
					}
					String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(
							(headers.get("sec-websocket-key") + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
					out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
					out.flush();
					wsOut = out;
					readFrames(in);
				}
				default -> respond(out, 404, "{}");
			}
		} catch (Exception ignored) {
			// connection closed
		}
	}

	private void readFrames(InputStream in) throws IOException {
		while (true) {
			int b0 = in.read();
			if (b0 == -1) {
				return;
			}
			int b1 = in.read();
			long len = b1 & 0x7F;
			if (len == 126) {
				len = (in.read() << 8) | in.read();
			} else if (len == 127) {
				len = 0;
				for (int i = 0; i < 8; i++) {
					len = (len << 8) | in.read();
				}
			}
			byte[] mask = (b1 & 0x80) != 0 ? in.readNBytes(4) : new byte[4];
			byte[] payload = in.readNBytes((int) len);
			for (int i = 0; i < payload.length; i++) {
				payload[i] ^= mask[i % 4];
			}
			int opcode = b0 & 0x0F;
			if (opcode == 0x8) {
				return;
			}
			if (opcode != 0x1) {
				continue;
			}
			String text = new String(payload, StandardCharsets.UTF_8);
			received.add(text);
			inbox.add(text);
			for (String reply : script.apply(text)) {
				send(reply);
			}
		}
	}

	private static void respond(OutputStream out, int status, String body) throws IOException {
		byte[] b = body.getBytes(StandardCharsets.UTF_8);
		out.write(("HTTP/1.1 " + status + " X\r\nContent-Type: application/json\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
		out.write(b);
		out.flush();
	}

	@Override
	public void close() throws IOException {
		server.close();
	}
}
