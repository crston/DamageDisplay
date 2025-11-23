package com.gmail.bobason01.util;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FontUtil {

    private static final Logger LOGGER = Logger.getLogger(FontUtil.class.getName());

    private FontUtil() {
    }

    public static CompletableFuture<Void> generateFontJsonRange(File outputDir, int start, int end, Executor executor) {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            LOGGER.warning("Could not create font dir " + outputDir.getAbsolutePath());
        }

        return CompletableFuture.runAsync(() -> {
            for (int i = start; i <= end; i++) {
                try {
                    String normal = buildProviderJson("damagedisplay:font/normal" + i + ".png");
                    String critical = buildProviderJson("damagedisplay:font/critical" + i + ".png");

                    FileUtil.writeJson(new File(outputDir, "normal" + i + ".json"), normal, executor).join();
                    FileUtil.writeJson(new File(outputDir, "critical" + i + ".json"), critical, executor).join();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to generate font json for index " + i, e);
                }
            }
            LOGGER.info("Generated font json from " + start + " to " + end);
        }, executor);
    }

    private static String buildProviderJson(String filePath) {
        String inner = "{\"type\":\"bitmap\",\"file\":\"" + filePath + "\",\"ascent\":32,\"height\":32,\"chars\":[\"0123456789\"]}";
        return "{\"providers\":[" + inner + "]}";
    }
}
