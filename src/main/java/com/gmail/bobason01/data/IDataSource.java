package com.gmail.bobason01.data;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IDataSource {

    CompletableFuture<Boolean> connect();

    CompletableFuture<Void> close();

    CompletableFuture<Integer> loadPlayerSkin(UUID uuid);

    CompletableFuture<Void> savePlayerSkin(UUID uuid, int skinIndex);
}
