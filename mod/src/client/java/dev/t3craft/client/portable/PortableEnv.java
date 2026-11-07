package dev.t3craft.client.portable;

/**
 * One environment row: connection status as the mod already reports it ({@code ok}, {@code connecting},
 * {@code offline}, {@code auth_failed}, {@code not_paired}, {@code incompatible}; bridge mode adds the
 * bridge's own words) plus counts over the threads currently shown.
 */
public record PortableEnv(String id, String label, String status, String detail, int threads, int needsAttention) {
	public boolean ok() {
		return "ok".equals(status);
	}
}
