package com.gmail.bobason01.data;

import java.util.UUID;
import java.util.concurrent.*;

/** 파일형 데이터소스 더미 */
public final class FileDataSource implements IDataSource {
    private static final CompletableFuture<Boolean> CONNECTED = CompletableFuture.completedFuture(true);
    private static final CompletableFuture<Void> VOID = CompletableFuture.completedFuture(null);
    private static final CompletableFuture<Integer> ZERO = CompletableFuture.completedFuture(0);

    public static final FileDataSource INSTANCE = new FileDataSource();
    private FileDataSource() {}

    public CompletionStage<Boolean> connect() { return CONNECTED; }
    public CompletionStage<Void> close() { return VOID; }
    public CompletionStage<Integer> loadPlayerSkin(UUID uuid) { return ZERO; }
    public CompletionStage<Void> savePlayerSkin(UUID uuid, int skinIndex) { return VOID; }
}
