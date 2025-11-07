package com.gmail.bobason01.data;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public sealed interface IDataSource
        permits FileDataSource, MemoryDataSource, MySQLDataSource, SQLiteDataSource, SqlDataSource, YamlDataSource {

    CompletionStage<Boolean> connect();
    CompletionStage<Void> close();
    CompletionStage<Integer> loadPlayerSkin(UUID uuid);
    CompletionStage<Void> savePlayerSkin(UUID uuid, int skinIndex);
}
