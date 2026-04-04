package com.gmail.bobason01.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FontUtil {

    private static final Logger LOGGER = Logger.getLogger(FontUtil.class.getName());

    private FontUtil() {}

    public static CompletableFuture<Void> generateFontJsonRange(File outputDir, int start, int end, Executor executor) {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            LOGGER.warning("Could not create font dir");
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = start; i <= end; i++) {
            final int index = i;
            String normalJson = buildProviderJson("damagedisplay:font/normal" + index + ".png");
            String criticalJson = buildProviderJson("damagedisplay:font/critical" + index + ".png");

            File normalFile = new File(outputDir, "normal" + index + ".json");
            File criticalFile = new File(outputDir, "critical" + index + ".json");

            futures.add(FileUtil.writeJson(normalFile, normalJson, executor));
            futures.add(FileUtil.writeJson(criticalFile, criticalJson, executor));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private static String buildProviderJson(String filePath) {
        return "{\"providers\":[{\"type\":\"bitmap\",\"file\":\"" + filePath + "\",\"ascent\":32,\"height\":32,\"chars\":[\"0123456789\"]}]}";
    }
}