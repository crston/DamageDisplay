package com.gmail.bobason01.data;

import java.util.UUID;
import java.util.concurrent.*;

/** 메모리 기반 초고속 데이터소스 */
public final class MemoryDataSource implements IDataSource {
    private static final CompletableFuture<Boolean> CONNECTED = CompletableFuture.completedFuture(true);
    private static final CompletableFuture<Void> VOID = CompletableFuture.completedFuture(null);
    private static final CompletableFuture<Integer> ZERO = CompletableFuture.completedFuture(0);

    public static final MemoryDataSource INSTANCE = new MemoryDataSource();
    private MemoryDataSource() {}

    public CompletionStage<Boolean> connect() { return CONNECTED; }
    public CompletionStage<Void> close() { return VOID; }
    public CompletionStage<Integer> loadPlayerSkin(UUID uuid) { return ZERO; }
    public CompletionStage<Void> savePlayerSkin(UUID uuid, int skinIndex) { return VOID; }
}
