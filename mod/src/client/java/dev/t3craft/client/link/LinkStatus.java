package dev.t3craft.client.link;

/** Connection state of the mod⇄bridge link, shown in logs (and later in a HUD banner). */
public enum LinkStatus {
	DISABLED,
	CONNECTING,
	CONNECTED,
	BACKOFF
}
