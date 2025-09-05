package com.gmail.bobason01.data;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IDataSource {
    void connect();
    void close();
    CompletableFuture<Integer> loadPlayerSkin(UUID uuid);
    void savePlayerSkin(UUID uuid, int skinIndex);
}