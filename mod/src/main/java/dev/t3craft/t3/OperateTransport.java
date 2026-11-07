package dev.t3craft.t3;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

/**
 * Sends one operate RPC to one environment and completes (never exceptionally) with the
 * {@link OperateResult}. Production: {@link OperateConnection}, owned by {@link DirectController}
 * next to the read-only {@link T3Connection}. Tests: a fake.
 */
public interface OperateTransport {
	CompletableFuture<OperateResult> call(String rpcTag, JsonObject payload);
}
